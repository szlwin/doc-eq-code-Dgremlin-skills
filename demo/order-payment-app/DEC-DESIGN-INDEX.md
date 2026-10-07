# DEC Design ID → Code Index

Source YAML is authoritative. Search its listed filename and Design ID to compare the design with generated Java. Runtime-managed nodes are compiled from design files and have no generated Java class.

| Design ID | YAML file | Kind | Generated Java |
| --- | --- | --- | --- |
| `ACT-CONFIRM-PAY-ERROR` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `ACT-CONFIRM-PAY-SUCCESS` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `ACT-RECEIVE-PAY-RESULT` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `ACT-RECORD-PAY-ERROR` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `ACT-RESET-PAY-RESULT` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `ACT-SAVE-ORDER` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `ACT-SMS-NOTIFY` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `ACT-START-PAY` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `API-ORDER-BACK-TO-PAYING` | `api-order.yaml` | api | [BackToPayingController.java](src/main/java/com/example/orderpayment/controller/BackToPayingController.java), [BackToPayingService.java](src/main/java/com/example/orderpayment/service/BackToPayingService.java) |
| `API-ORDER-GET` | `api-order.yaml` | api | [GetOrderController.java](src/main/java/com/example/orderpayment/controller/GetOrderController.java), [GetOrderService.java](src/main/java/com/example/orderpayment/service/GetOrderService.java) |
| `API-ORDER-RECEIVE-PAYMENT-RESULT` | `api-order.yaml` | api | [ReceivePaymentResultController.java](src/main/java/com/example/orderpayment/controller/ReceivePaymentResultController.java), [ReceivePaymentResultService.java](src/main/java/com/example/orderpayment/service/ReceivePaymentResultService.java) |
| `API-ORDER-START-PAYMENT` | `api-order.yaml` | api | [StartPaymentController.java](src/main/java/com/example/orderpayment/controller/StartPaymentController.java), [StartPaymentService.java](src/main/java/com/example/orderpayment/service/StartPaymentService.java) |
| `API-ORDER-SUBMIT` | `api-order.yaml` | api | [SubmitOrderController.java](src/main/java/com/example/orderpayment/controller/SubmitOrderController.java), [SubmitOrderService.java](src/main/java/com/example/orderpayment/service/SubmitOrderService.java) |
| `API-PARAM-BACKTOPAYING-ORDER-ID` | `api-order.yaml` | api | [BackToPayingController.java](src/main/java/com/example/orderpayment/controller/BackToPayingController.java) |
| `API-PARAM-GETORDER-ORDER-ID` | `api-order.yaml` | api | [GetOrderController.java](src/main/java/com/example/orderpayment/controller/GetOrderController.java) |
| `API-PARAM-ORDER-ID` | `api-order.yaml` | api | [SubmitOrderController.java](src/main/java/com/example/orderpayment/controller/SubmitOrderController.java) |
| `API-PARAM-PAY-RESULT-CODE` | `api-order.yaml` | api | [ReceivePaymentResultRequest.java](src/main/java/com/example/orderpayment/dto/ReceivePaymentResultRequest.java) |
| `API-PARAM-PRODUCT-COUNT` | `api-order.yaml` | api | [SubmitOrderRequest.java](src/main/java/com/example/orderpayment/dto/SubmitOrderRequest.java) |
| `API-PARAM-PRODUCT-ID` | `api-order.yaml` | api | [SubmitOrderRequest.java](src/main/java/com/example/orderpayment/dto/SubmitOrderRequest.java) |
| `API-PARAM-PRODUCT-NAME` | `api-order.yaml` | api | [SubmitOrderRequest.java](src/main/java/com/example/orderpayment/dto/SubmitOrderRequest.java) |
| `API-PARAM-PRODUCT-PRICE` | `api-order.yaml` | api | [SubmitOrderRequest.java](src/main/java/com/example/orderpayment/dto/SubmitOrderRequest.java) |
| `API-PARAM-RECEIVEPAYMENTRESULT-ORDER-ID` | `api-order.yaml` | api | [ReceivePaymentResultController.java](src/main/java/com/example/orderpayment/controller/ReceivePaymentResultController.java) |
| `API-PARAM-REMARK` | `api-order.yaml` | api | [SubmitOrderRequest.java](src/main/java/com/example/orderpayment/dto/SubmitOrderRequest.java) |
| `API-PARAM-STARTPAYMENT-ORDER-ID` | `api-order.yaml` | api | [StartPaymentController.java](src/main/java/com/example/orderpayment/controller/StartPaymentController.java) |
| `API-PARAM-STATUS` | `api-order.yaml` | api | [SubmitOrderRequest.java](src/main/java/com/example/orderpayment/dto/SubmitOrderRequest.java) |
| `API-PARAM-TYPE` | `api-order.yaml` | api | [SubmitOrderRequest.java](src/main/java/com/example/orderpayment/dto/SubmitOrderRequest.java) |
| `API-PARAM-USER-ID` | `api-order.yaml` | api | [SubmitOrderRequest.java](src/main/java/com/example/orderpayment/dto/SubmitOrderRequest.java) |
| `BUS-ORDER-PAYMENT` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `DATA-ORDER` | `data-order.yaml` | data | [Order.java](src/main/java/com/example/orderpayment/entity/Order.java), [OrderDao.java](src/main/java/com/example/orderpayment/dao/OrderDao.java), [OrderMapper.java](src/main/java/com/example/orderpayment/mapper/OrderMapper.java) |
| `DATA-ORDER-DETAIL` | `data-order.yaml` | data | [OrderDetail.java](src/main/java/com/example/orderpayment/entity/OrderDetail.java), [OrderDetailDao.java](src/main/java/com/example/orderpayment/dao/OrderDetailDao.java), [OrderDetailMapper.java](src/main/java/com/example/orderpayment/mapper/OrderDetailMapper.java) |
| `DATA-PAY` | `data-order.yaml` | data | [Pay.java](src/main/java/com/example/orderpayment/entity/Pay.java), [PayDao.java](src/main/java/com/example/orderpayment/dao/PayDao.java), [PayMapper.java](src/main/java/com/example/orderpayment/mapper/PayMapper.java) |
| `DATA-PAY-DETAIL` | `data-order.yaml` | data | [PayDetail.java](src/main/java/com/example/orderpayment/entity/PayDetail.java), [PayDetailDao.java](src/main/java/com/example/orderpayment/dao/PayDetailDao.java), [PayDetailMapper.java](src/main/java/com/example/orderpayment/mapper/PayDetailMapper.java) |
| `DATA-USER` | `data-order.yaml` | data | [User.java](src/main/java/com/example/orderpayment/entity/User.java), [UserDao.java](src/main/java/com/example/orderpayment/dao/UserDao.java), [UserMapper.java](src/main/java/com/example/orderpayment/mapper/UserMapper.java) |
| `DIR-ERROR` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `DIR-ORDERED` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `DIR-PAY-RESULT` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `DIR-PAYING` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `DIR-SUCCESS` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `ENUM-ORDER-STATUS` | `enum-common.yaml` | enum | [OrderStatus.java](src/main/java/com/example/orderpayment/enums/OrderStatus.java) |
| `ENUM-ORDER-TYPE` | `enum-common.yaml` | enum | [OrderType.java](src/main/java/com/example/orderpayment/enums/OrderType.java) |
| `ENUM-PAY-RESULT-CODE` | `enum-common.yaml` | enum | [PayResultCode.java](src/main/java/com/example/orderpayment/enums/PayResultCode.java) |
| `ENUM-PAYMENT-STATUS` | `enum-common.yaml` | enum | [PaymentStatus.java](src/main/java/com/example/orderpayment/enums/PaymentStatus.java) |
| `ENUM-VALUE-ORDER-STATUS-ERROR` | `enum-common.yaml` | enum | [OrderStatus.java](src/main/java/com/example/orderpayment/enums/OrderStatus.java) |
| `ENUM-VALUE-ORDER-STATUS-PAYING` | `enum-common.yaml` | enum | [OrderStatus.java](src/main/java/com/example/orderpayment/enums/OrderStatus.java) |
| `ENUM-VALUE-ORDER-STATUS-SUCCESS` | `enum-common.yaml` | enum | [OrderStatus.java](src/main/java/com/example/orderpayment/enums/OrderStatus.java) |
| `ENUM-VALUE-ORDER-STATUS-WAIT-PAY` | `enum-common.yaml` | enum | [OrderStatus.java](src/main/java/com/example/orderpayment/enums/OrderStatus.java) |
| `ENUM-VALUE-ORDER-TYPE-NORMAL` | `enum-common.yaml` | enum | [OrderType.java](src/main/java/com/example/orderpayment/enums/OrderType.java) |
| `ENUM-VALUE-ORDER-TYPE-PREORDER` | `enum-common.yaml` | enum | [OrderType.java](src/main/java/com/example/orderpayment/enums/OrderType.java) |
| `ENUM-VALUE-PAY-RESULT-ERROR` | `enum-common.yaml` | enum | [PayResultCode.java](src/main/java/com/example/orderpayment/enums/PayResultCode.java) |
| `ENUM-VALUE-PAY-RESULT-SUCCESS` | `enum-common.yaml` | enum | [PayResultCode.java](src/main/java/com/example/orderpayment/enums/PayResultCode.java) |
| `ENUM-VALUE-PAYMENT-STATUS-INIT` | `enum-common.yaml` | enum | [PaymentStatus.java](src/main/java/com/example/orderpayment/enums/PaymentStatus.java) |
| `ENUM-VALUE-PAYMENT-STATUS-PROCESSING` | `enum-common.yaml` | enum | [PaymentStatus.java](src/main/java/com/example/orderpayment/enums/PaymentStatus.java) |
| `INFO-COMMON-PAY-ERROR` | `systems-order.yaml` | systems | DEC Runtime / application binding |
| `INFO-COMMON-PAY-SUCCESS` | `systems-order.yaml` | systems | DEC Runtime / application binding |
| `INFO-ORDER-ORDERED` | `systems-order.yaml` | systems | DEC Runtime / application binding |
| `INFO-ORDER-PAY-ERROR-STATUS` | `systems-order.yaml` | systems | DEC Runtime / application binding |
| `INFO-ORDER-PAY-SUCCESS-STATUS` | `systems-order.yaml` | systems | DEC Runtime / application binding |
| `INFO-ORDER-PAYABLE` | `systems-order.yaml` | systems | DEC Runtime / application binding |
| `INFO-ORDER-PAYING` | `systems-order.yaml` | systems | DEC Runtime / application binding |
| `INFO-ORDER-WAIT-PAY` | `systems-order.yaml` | systems | DEC Runtime / application binding |
| `INFO-PAYMENT-COMPLETED` | `systems-order.yaml` | systems | DEC Runtime / application binding |
| `INFO-PAYMENT-ERROR` | `systems-order.yaml` | systems | DEC Runtime / application binding |
| `INFO-PAYMENT-HAS-RESULT` | `systems-order.yaml` | systems | DEC Runtime / application binding |
| `INFO-PAYMENT-INFO` | `systems-order.yaml` | systems | DEC Runtime / application binding |
| `INFO-PAYMENT-SUCCESS` | `systems-order.yaml` | systems | DEC Runtime / application binding |
| `INFO-USER-ACTIVATED` | `systems-order.yaml` | systems | DEC Runtime / application binding |
| `INFO-USER-CERTIFIED` | `systems-order.yaml` | systems | DEC Runtime / application binding |
| `INFO-USER-EFFECTIVE` | `systems-order.yaml` | systems | DEC Runtime / application binding |
| `PROD-ORDER` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `PROD-PAY-ERROR` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `PROD-PAY-RESULT` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `PROD-PAYMENT-INFO` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `RULE-CONFIRM-PAY-ERROR` | `rule-order.yaml` | rule | DEC Runtime / application binding |
| `RULE-CONFIRM-PAY-SUCCESS` | `rule-order.yaml` | rule | DEC Runtime / application binding |
| `RULE-HAS-PAY-RESULT` | `rule-payment.yaml` | rule | DEC Runtime / application binding |
| `RULE-HAS-PAYMENT-INFO` | `rule-payment.yaml` | rule | DEC Runtime / application binding |
| `RULE-INSERT-DETAIL` | `rule-order.yaml` | rule | DEC Runtime / application binding |
| `RULE-INSERT-ORDER` | `rule-order.yaml` | rule | DEC Runtime / application binding |
| `RULE-INSERT-PAY` | `rule-payment.yaml` | rule | DEC Runtime / application binding |
| `RULE-IS-PAY-ERROR` | `rule-payment.yaml` | rule | DEC Runtime / application binding |
| `RULE-IS-PAY-SUCCESS` | `rule-payment.yaml` | rule | DEC Runtime / application binding |
| `RULE-ORDER-IS-WAIT-PAY` | `rule-order.yaml` | rule | DEC Runtime / application binding |
| `RULE-RECEIVE-PAY-RESULT` | `rule-payment.yaml` | rule | DEC Runtime / application binding |
| `RULE-RECORD-PAY-ERROR` | `rule-payment.yaml` | rule | DEC Runtime / application binding |
| `RULE-RESET-PAY-RESULT` | `rule-payment.yaml` | rule | DEC Runtime / application binding |
| `RULE-USER-IS-ACTIVATED` | `rule-user.yaml` | rule | DEC Runtime / application binding |
| `RULE-USER-IS-CERTIFIED` | `rule-user.yaml` | rule | DEC Runtime / application binding |
| `RV-CONFIRM-PAY-ERROR` | `rule-order.yaml` | rule | DEC Runtime / application binding |
| `RV-CONFIRM-PAY-SUCCESS` | `rule-order.yaml` | rule | DEC Runtime / application binding |
| `RV-HAS-PAY-RESULT` | `rule-payment.yaml` | rule | DEC Runtime / application binding |
| `RV-HAS-PAYMENT-INFO` | `rule-payment.yaml` | rule | DEC Runtime / application binding |
| `RV-IS-PAY-ERROR` | `rule-payment.yaml` | rule | DEC Runtime / application binding |
| `RV-IS-PAY-SUCCESS` | `rule-payment.yaml` | rule | DEC Runtime / application binding |
| `RV-ORDER-IS-WAIT-PAY` | `rule-order.yaml` | rule | DEC Runtime / application binding |
| `RV-PAY` | `rule-payment.yaml` | rule | DEC Runtime / application binding |
| `RV-RECEIVE-PAY-RESULT` | `rule-payment.yaml` | rule | DEC Runtime / application binding |
| `RV-RECORD-PAY-ERROR` | `rule-payment.yaml` | rule | DEC Runtime / application binding |
| `RV-RESET-PAY-RESULT` | `rule-payment.yaml` | rule | DEC Runtime / application binding |
| `RV-SAVE-ORDER` | `rule-order.yaml` | rule | DEC Runtime / application binding |
| `RV-USER-IS-ACTIVATED` | `rule-user.yaml` | rule | DEC Runtime / application binding |
| `RV-USER-IS-CERTIFIED` | `rule-user.yaml` | rule | DEC Runtime / application binding |
| `SYS-COMMON` | `systems-order.yaml` | systems | DEC Runtime / application binding |
| `SYS-ORDER` | `systems-order.yaml` | systems | DEC Runtime / application binding |
| `SYS-PAYMENT` | `systems-order.yaml` | systems | DEC Runtime / application binding |
| `SYS-USER` | `systems-order.yaml` | systems | DEC Runtime / application binding |
| `VIEW-ORDER-INFO` | `view-order.yaml` | view | [OrderInfo.java](src/main/java/com/example/orderpayment/view/OrderInfo.java) |
| `VIEW-USER-INFO` | `view-order.yaml` | view | [UserInfo.java](src/main/java/com/example/orderpayment/view/UserInfo.java) |
