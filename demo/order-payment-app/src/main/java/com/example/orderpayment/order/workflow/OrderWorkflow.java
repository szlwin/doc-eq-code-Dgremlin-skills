package com.example.orderpayment.order.workflow;
import com.dec.lite.directory.WorkflowTransaction;
import com.dec.lite.spring.SpringWorkflowTransactions;
import com.dec.lite.action.CustomActionRegistry;
import com.dec.lite.action.ActionListenerRegistry;
import com.dec.lite.action.DataSourceAdapterRegistry;
import com.dec.lite.action.DataSourceSelection;
import com.dec.lite.directory.DirectoryWorkflowException;
import com.dec.lite.directory.DirectoryExecutionException;
import jakarta.validation.Validator;
import com.dec.lite.directory.DirectoryWorkflowRunner;
import com.dec.lite.information.ModelContext;
import com.dec.lite.parser.DecXmlParser;
import com.example.orderpayment.order.dto.SubmitOrderRequest;
import com.example.orderpayment.business.OrderPaymentFlow;
import com.example.orderpayment.order.entity.Order;
import com.example.orderpayment.order.entity.OrderDetail;
import com.example.orderpayment.order.store.LocalOrderStore;
import com.example.orderpayment.order.store.LocalOrderStoreFactory;
import com.example.orderpayment.order.view.OrderViewAssembler;
import com.example.orderpayment.user.entity.User;
import com.example.orderpayment.payment.enums.PayResultCode;
import com.example.orderpayment.order.view.OrderInfo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/** Coordinates the canonical Directory; View/Entity and DAO work live in dedicated collaborators. */
@Component
public class OrderWorkflow {
    private final LocalOrderStore store;
    private final Validator validator;
    public static final Map<String, String> DEFAULT_DATA_SOURCES = Map.of(
            OrderPaymentFlow.DATA_SOURCE, "local", "paymentService", "local", "mongo1", "local");
    private final DataSourceAdapterRegistry dataSources;
    private final DirectoryWorkflowRunner runtime;
    private final WorkflowTransaction transaction = SpringWorkflowTransactions.externallyManaged();
    private final CustomActionRegistry customActions;
    private final OrderViewAssembler assembler = new OrderViewAssembler();
    private final DecViewModelBridge bridge = new DecViewModelBridge();

    public OrderWorkflow(LocalOrderStoreFactory storeFactory, CustomActionRegistry customActions,
                         DataSourceAdapterRegistry dataSources, Validator validator, ActionListenerRegistry listeners) {
        this.validator = validator;
        this.store = storeFactory.create();
        this.dataSources = dataSources;
        this.customActions = customActions;
        runtime = new DirectoryWorkflowRunner(new DecXmlParser().parseClasspath(OrderWorkflow.class.getClassLoader(), "dec/design-files.txt"), customActions, listeners);
    }

    /** API-ORDER-SUBMIT → DIR-ORDERED / ACT-SAVE-ORDER; status/type are checked by RV-SAVE-ORDER. */
    @Transactional
    public OrderInfo submitOrder(SubmitOrderRequest request, Long orderId) {
        return submitOrder(request, orderId, DEFAULT_DATA_SOURCES);
    }

    @Transactional
    public OrderInfo submitOrder(SubmitOrderRequest request, Long orderId, Map<String, String> selection) {
        DataSourceSelection rules = runtime.selectDataSources(dataSources, selection);
        requireId(orderId);
        if (store.orders().find(orderId) != null)
            throw failure(HttpStatus.CONFLICT, "Order already exists: " + orderId);
        if (request == null || !validator.validate(request).isEmpty())
            throw failure(HttpStatus.BAD_REQUEST, "API-ORDER-SUBMIT request violates canonical API contract");
        int id = orderId.intValue();
        BigDecimal amount = request.getProductPrice().multiply(BigDecimal.valueOf(request.getProductCount()));
        Order order = new Order();
        order.setId(id); order.setUserId(request.getUserId()); order.setProductCount(request.getProductCount());
        order.setTotalPrice(amount); order.setTotalAmount(amount); order.setCreateDate(LocalDate.now());
        order.setOrderStatus(request.getStatus());
        order.setRemark(request.getRemark()); order.setOrderType(request.getType());
        OrderDetail detail = new OrderDetail();
        detail.setId(id); detail.setOrderId(id); detail.setUserId(request.getUserId());
        detail.setProductId(request.getProductId()); detail.setProductName(request.getProductName());
        detail.setProductPrice(request.getProductPrice()); detail.setProductAmount(amount);
        detail.setStatus(request.getStatus());
        User user = store.users().find(request.getUserId().longValue());
        if (user == null) {
            user = new User(); user.setId(request.getUserId());
            user.setName("demo-user-" + user.getId()); user.setActive(user.getId() == 2 ? 0 : 1);
            user.setCertified(1);
            store.users().insert(user);
        }
        return advance(orderId, true, assembler.draft(order, detail, user), OrderPaymentFlow.ORDERED.name(), null, false, rules);
    }

    /** API-ORDER-START-PAYMENT → DIR-PAYING / ACT-START-PAY. */
    @Transactional
    public OrderInfo startPayment(Long orderId) { return startPayment(orderId, DEFAULT_DATA_SOURCES); }

    @Transactional
    public OrderInfo startPayment(Long orderId, Map<String, String> selection) {
        DataSourceSelection rules = runtime.selectDataSources(dataSources, selection);
        requireOrderForUpdate(orderId);
        return advance(orderId, false, assembler.read(store, orderId), OrderPaymentFlow.PAYING.name(), null, false, rules);
    }

    /** API-ORDER-RECEIVE-PAYMENT-RESULT → DIR-PAY-RESULT and its selected case. */
    @Transactional
    public OrderInfo receivePaymentResult(Long orderId, PayResultCode resultCode) {
        return receivePaymentResult(orderId, resultCode, DEFAULT_DATA_SOURCES);
    }

    @Transactional
    public OrderInfo receivePaymentResult(Long orderId, PayResultCode resultCode, Map<String, String> selection) {
        DataSourceSelection rules = runtime.selectDataSources(dataSources, selection);
        if (resultCode == null) throw failure(HttpStatus.BAD_REQUEST, "resultCode is required");
        requireOrderForUpdate(orderId);
        return advance(orderId, false, assembler.read(store, orderId), OrderPaymentFlow.PAY_RESULT.name(), resultCode, false, rules);
    }

    /** API-ORDER-BACK-TO-PAYING → ACT-RESET-PAY-RESULT. */
    @Transactional
    public OrderInfo backToPaying(Long orderId) { return backToPaying(orderId, DEFAULT_DATA_SOURCES); }

    @Transactional
    public OrderInfo backToPaying(Long orderId, Map<String, String> selection) {
        DataSourceSelection rules = runtime.selectDataSources(dataSources, selection);
        requireOrderForUpdate(orderId);
        return advance(orderId, false, assembler.read(store, orderId), OrderPaymentFlow.PAYING.name(), null, true, rules);
    }

    /** API-ORDER-GET → VIEW-ORDER-INFO assembled through the generated DAOs. */
    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long orderId) { return getOrder(orderId, DEFAULT_DATA_SOURCES); }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long orderId, Map<String, String> selection) {
        DataSourceSelection rules = runtime.selectDataSources(dataSources, selection);
        requireOrder(orderId);
        OrderInfo persisted = assembler.read(store, orderId);
        try {
            runtime.recognizeDirectory(bridge.toModel(persisted),
                    rules);
            return persisted;
        } catch (DirectoryExecutionException failure) {
            throw failure(HttpStatus.CONFLICT, failure.getMessage());
        }
    }

    private OrderInfo advance(Long orderId, boolean creating,
                              OrderInfo view, String target, PayResultCode resultCode, boolean back, DataSourceSelection rules) {
        try {
            String current = creating ? null : runtime.recognizeDirectory(bridge.toModel(view), rules);
            runtime.requireSingleStep(current, target, back);
            return runtime.advance(view, current, target, back,
                    resultCode == null ? Map.of() : Map.of("resultCode", resultCode.getValue()),
                    rules, customActions,
                    transaction, Instant.now().plusSeconds(30),
                    new DirectoryWorkflowRunner.ModelCodec<>() {
                        public ModelContext toModel(OrderInfo value) { return bridge.toModel(value); }
                        public OrderInfo fromModel(ModelContext model) { return bridge.fromModel(model); }
                    }, (completed, execution, scope) -> {
                        assembler.persistFinal(store, completed);
                        OrderInfo persisted = assembler.read(store, orderId);
                        runtime.assertDirectoryMatches(bridge.toModel(persisted), execution.currentDirectory(), rules);
                        return persisted;
                    });
        } catch (DirectoryWorkflowException | DirectoryExecutionException failure) {
            throw failure(HttpStatus.CONFLICT, failure.getMessage());
        }
    }

    private void requireOrder(Long id) {
        requireId(id);
        if (store.orders().find(id) == null) throw failure(HttpStatus.NOT_FOUND, "Order does not exist: " + id);
    }
    private void requireOrderForUpdate(Long id) {
        requireId(id);
        store.lockOrder(id);
        requireOrder(id);
    }
    private static void requireId(Long id) {
        if (id == null || id < 1 || id > 999999999) throw failure(HttpStatus.BAD_REQUEST, "orderId must be 1..999999999");
    }
    private static ResponseStatusException failure(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
