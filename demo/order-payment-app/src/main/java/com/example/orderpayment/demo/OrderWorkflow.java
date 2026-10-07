package com.example.orderpayment.demo;

import com.dec.lite.action.ActionExecutionContext;
import com.dec.lite.action.ActionRuntime;
import com.dec.lite.action.ActionTransaction;
import com.dec.lite.action.CustomAction;
import com.dec.lite.action.CustomActionRegistry;
import com.dec.lite.action.RuleViewCompiler;
import com.dec.lite.directory.DirectoryEngine;
import com.dec.lite.directory.DirectoryExecutionContext;
import com.dec.lite.directory.DirectoryExecutionResult;
import com.dec.lite.directory.DirectoryGraphCompiler;
import com.dec.lite.information.InformationCompilation;
import com.dec.lite.information.InformationEngine;
import com.dec.lite.information.InformationEngineContext;
import com.dec.lite.information.InformationParser;
import com.dec.lite.information.ModelContext;
import com.dec.lite.information.RuleViewRegistry;
import com.dec.lite.model.DecProject;
import com.dec.lite.parser.DecXmlParser;
import com.example.orderpayment.dto.SubmitOrderRequest;
import com.example.orderpayment.entity.Order;
import com.example.orderpayment.entity.OrderDetail;
import com.example.orderpayment.entity.User;
import com.example.orderpayment.enums.OrderStatus;
import com.example.orderpayment.enums.OrderType;
import com.example.orderpayment.enums.PayResultCode;
import com.example.orderpayment.view.OrderInfo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Coordinates the canonical Directory; View/Entity and DAO work live in dedicated collaborators. */
@Component
public final class OrderWorkflow {
    private static final List<String> DESIGN_FILES = List.of(
            "api-order.xml", "business-order-payment.xml", "data-order.xml", "enum-common.xml",
            "orm-config.xml", "rule-order.xml", "rule-payment.xml", "rule-user.xml",
            "systems-order.xml", "view-order.xml");
    private final LocalOrderStore store;
    private final DecProject project;
    private final InformationCompilation information;
    private final DirectoryEngine directory;
    private final OrderViewAssembler assembler = new OrderViewAssembler();
    private final DecViewModelBridge bridge = new DecViewModelBridge();

    public OrderWorkflow(LocalOrderStore store) {
        this.store = store;
        project = loadDesign();
        information = new InformationParser().parse(project);
        directory = new DirectoryEngine(new DirectoryGraphCompiler().compileReady(
                project, information, customActions(null, -1L)));
    }

    /** API-ORDER-SUBMIT → DIR-ORDERED / ACT-SAVE-ORDER. */
    public synchronized OrderInfo submitOrder(SubmitOrderRequest request, Long orderId) {
        requireId(orderId);
        if (store.directory(orderId) != null) throw failure(HttpStatus.CONFLICT, "Order already exists: " + orderId);
        if (request == null || request.getUserId() == null || request.getUserId() < 1
                || request.getProductId() == null || request.getProductId() < 1
                || request.getProductName() == null || request.getProductName().isBlank()
                || request.getProductPrice() == null || request.getProductPrice().compareTo(BigDecimal.ONE) < 0
                || request.getProductCount() == null || request.getProductCount() < 1
                || request.getStatus() != OrderStatus.WAIT_PAY || request.getType() != OrderType.NORMAL)
            throw failure(HttpStatus.BAD_REQUEST, "API-ORDER-SUBMIT request violates canonical API contract");
        int id = orderId.intValue();
        BigDecimal amount = request.getProductPrice().multiply(BigDecimal.valueOf(request.getProductCount()));
        Order order = new Order();
        order.setId(id); order.setUserId(request.getUserId()); order.setProductCount(request.getProductCount());
        order.setTotalPrice(amount); order.setTotalAmount(amount); order.setCreateDate(LocalDate.now());
        order.setRemark(request.getRemark()); order.setOrderType(request.getType());
        OrderDetail detail = new OrderDetail();
        detail.setId(id); detail.setOrderId(id); detail.setUserId(request.getUserId());
        detail.setProductId(request.getProductId()); detail.setProductName(request.getProductName());
        detail.setProductPrice(request.getProductPrice()); detail.setProductAmount(amount);
        LocalOrderStore.UnitOfWork unit = store.begin();
        User user = unit.users().find(request.getUserId().longValue());
        if (user == null) {
            user = new User(); user.setId(request.getUserId());
            user.setName("demo-user-" + user.getId()); user.setActive(user.getId() == 2 ? 0 : 1);
            user.setCertified(1);
            unit.users().insert(user);
        }
        return advance(orderId, unit, null, assembler.draft(order, detail, user), "ordered", null, false);
    }

    /** API-ORDER-START-PAYMENT → DIR-PAYING / ACT-START-PAY. */
    public synchronized OrderInfo startPayment(Long orderId) {
        LocalOrderStore.UnitOfWork unit = requireOrder(orderId);
        requireDirectory(orderId, "ordered");
        return advance(orderId, unit, "ordered", assembler.read(unit, orderId), "paying", null, false);
    }

    /** API-ORDER-RECEIVE-PAYMENT-RESULT → DIR-PAY-RESULT and its selected case. */
    public synchronized OrderInfo receivePaymentResult(Long orderId, PayResultCode resultCode) {
        if (resultCode == null) throw failure(HttpStatus.BAD_REQUEST, "resultCode is required");
        LocalOrderStore.UnitOfWork unit = requireOrder(orderId);
        requireDirectory(orderId, "paying");
        return advance(orderId, unit, "paying", assembler.read(unit, orderId), "PayResult", resultCode, false);
    }

    /** API-ORDER-BACK-TO-PAYING → ACT-RESET-PAY-RESULT. */
    public synchronized OrderInfo backToPaying(Long orderId) {
        LocalOrderStore.UnitOfWork unit = requireOrder(orderId);
        String current = store.directory(orderId);
        if (!"success".equals(current) && !"error".equals(current))
            throw failure(HttpStatus.CONFLICT, "Back requires a completed payment result");
        return advance(orderId, unit, current, assembler.read(unit, orderId), "paying", null, true);
    }

    /** API-ORDER-GET → VIEW-ORDER-INFO assembled through the generated DAOs. */
    public synchronized OrderInfo getOrder(Long orderId) {
        LocalOrderStore.UnitOfWork unit = requireOrder(orderId);
        return assembler.read(unit, orderId);
    }

    public List<Long> smsNotifications() { return store.smsNotifications(); }
    public List<LocalOrderStore.PaymentError> paymentErrors() { return store.paymentErrors(); }

    private OrderInfo advance(Long orderId, LocalOrderStore.UnitOfWork unit, String current,
                              OrderInfo view, String target, PayResultCode resultCode, boolean back) {
        RuleViewRegistry ruleViews = new RuleViewCompiler().register(project,
                new LocalRuleAdapter(unit, bridge, assembler)).freeze();
        InformationEngine engine = new InformationEngine(new InformationEngineContext(information, ruleViews));
        ModelContext model = bridge.toModel(view);
        DirectoryExecutionContext context = new DirectoryExecutionContext(engine,
                new ActionRuntime(ruleViews, customActions(unit, orderId)), model,
                resultCode == null ? Map.of() : Map.of("resultCode", resultCode.getValue()),
                new ActionTransaction() { }, Instant.now().plusSeconds(30), current);
        DirectoryExecutionResult result = back ? directory.backTo(target, context) : directory.executeTo(target, context);
        if (!result.success()) throw failure(HttpStatus.CONFLICT,
                "DEC workflow " + target + " failed: " + result.error());
        OrderInfo completed = bridge.fromModel(context.model());
        assembler.persistFinal(unit, completed);
        unit.commit(orderId, result.currentDirectory());
        return assembler.read(unit, orderId);
    }

    private static CustomActionRegistry customActions(LocalOrderStore.UnitOfWork unit, Long orderId) {
        return new CustomActionRegistry().register(new CustomAction() {
            public String name() { return "smsNotify"; }
            public void execute(ActionExecutionContext context) { if (unit != null) unit.notifySms(orderId); }
        });
    }

    private static DecProject loadDesign() {
        try {
            Path directory = Files.createTempDirectory("dec-order-payment-");
            directory.toFile().deleteOnExit();
            for (String name : DESIGN_FILES) {
                try (InputStream source = OrderWorkflow.class.getClassLoader().getResourceAsStream("dec/design/" + name)) {
                    if (source == null) throw new IllegalStateException("Missing packaged DEC design: " + name);
                    Path target = directory.resolve(name);
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    target.toFile().deleteOnExit();
                }
            }
            return new DecXmlParser().parse(directory);
        } catch (Exception failure) { throw new IllegalStateException("Cannot initialize DEC order-payment design", failure); }
    }

    private LocalOrderStore.UnitOfWork requireOrder(Long id) {
        requireId(id);
        if (store.directory(id) == null) throw failure(HttpStatus.NOT_FOUND, "Order does not exist: " + id);
        return store.begin();
    }
    private static void requireId(Long id) {
        if (id == null || id < 1 || id > 999999999) throw failure(HttpStatus.BAD_REQUEST, "orderId must be 1..999999999");
    }
    private void requireDirectory(Long id, String expected) {
        String current = store.directory(id);
        if (!expected.equals(current)) throw failure(HttpStatus.CONFLICT,
                "Expected Directory " + expected + ", found " + current);
    }
    private static ResponseStatusException failure(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
