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
| `API-ORDER-SUBMIT` | `api-order.yaml` | api | [SubmitOrderController.java](src/main/java/com/example/order/controller/SubmitOrderController.java), [SubmitOrderService.java](src/main/java/com/example/order/service/SubmitOrderService.java) |
| `API-PARAM-ORDER-ID` | `api-order.yaml` | api | [SubmitOrderController.java](src/main/java/com/example/order/controller/SubmitOrderController.java) |
| `API-PARAM-REMARK` | `api-order.yaml` | api | [SubmitOrderRequest.java](src/main/java/com/example/order/dto/SubmitOrderRequest.java) |
| `API-PARAM-STATUS` | `api-order.yaml` | api | [SubmitOrderRequest.java](src/main/java/com/example/order/dto/SubmitOrderRequest.java) |
| `API-PARAM-TYPE` | `api-order.yaml` | api | [SubmitOrderRequest.java](src/main/java/com/example/order/dto/SubmitOrderRequest.java) |
| `BUS-ORDER-PAYMENT` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `DATA-ORDER` | `data-order.yaml` | data | [Order.java](src/main/java/com/example/order/entity/Order.java), [OrderDao.java](src/main/java/com/example/order/dao/OrderDao.java), [OrderMapper.java](src/main/java/com/example/order/mapper/OrderMapper.java) |
| `DATA-ORDER-DETAIL` | `data-order.yaml` | data | [OrderDetail.java](src/main/java/com/example/order/entity/OrderDetail.java), [OrderDetailDao.java](src/main/java/com/example/order/dao/OrderDetailDao.java), [OrderDetailMapper.java](src/main/java/com/example/order/mapper/OrderDetailMapper.java) |
| `DATA-PAY` | `data-order.yaml` | data | [Pay.java](src/main/java/com/example/order/entity/Pay.java), [PayDao.java](src/main/java/com/example/order/dao/PayDao.java), [PayMapper.java](src/main/java/com/example/order/mapper/PayMapper.java) |
| `DATA-PAY-DETAIL` | `data-order.yaml` | data | [PayDetail.java](src/main/java/com/example/order/entity/PayDetail.java), [PayDetailDao.java](src/main/java/com/example/order/dao/PayDetailDao.java), [PayDetailMapper.java](src/main/java/com/example/order/mapper/PayDetailMapper.java) |
| `DATA-USER` | `data-order.yaml` | data | [User.java](src/main/java/com/example/order/entity/User.java), [UserDao.java](src/main/java/com/example/order/dao/UserDao.java), [UserMapper.java](src/main/java/com/example/order/mapper/UserMapper.java) |
| `DIR-ERROR` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `DIR-ORDERED` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `DIR-PAY-RESULT` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `DIR-PAYING` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `DIR-SUCCESS` | `business-order-payment.yaml` | business | DEC Runtime / application binding |
| `ENUM-ORDER-STATUS` | `enum-common.yaml` | enum | [OrderStatus.java](src/main/java/com/example/order/enums/OrderStatus.java) |
| `ENUM-ORDER-TYPE` | `enum-common.yaml` | enum | [OrderType.java](src/main/java/com/example/order/enums/OrderType.java) |
| `ENUM-PAY-RESULT-CODE` | `enum-common.yaml` | enum | [PayResultCode.java](src/main/java/com/example/order/enums/PayResultCode.java) |
| `ENUM-PAYMENT-STATUS` | `enum-common.yaml` | enum | [PaymentStatus.java](src/main/java/com/example/order/enums/PaymentStatus.java) |
| `ENUM-VALUE-ORDER-STATUS-ERROR` | `enum-common.yaml` | enum | [OrderStatus.java](src/main/java/com/example/order/enums/OrderStatus.java) |
| `ENUM-VALUE-ORDER-STATUS-PAYING` | `enum-common.yaml` | enum | [OrderStatus.java](src/main/java/com/example/order/enums/OrderStatus.java) |
| `ENUM-VALUE-ORDER-STATUS-SUCCESS` | `enum-common.yaml` | enum | [OrderStatus.java](src/main/java/com/example/order/enums/OrderStatus.java) |
| `ENUM-VALUE-ORDER-STATUS-WAIT-PAY` | `enum-common.yaml` | enum | [OrderStatus.java](src/main/java/com/example/order/enums/OrderStatus.java) |
| `ENUM-VALUE-ORDER-TYPE-NORMAL` | `enum-common.yaml` | enum | [OrderType.java](src/main/java/com/example/order/enums/OrderType.java) |
| `ENUM-VALUE-ORDER-TYPE-PREORDER` | `enum-common.yaml` | enum | [OrderType.java](src/main/java/com/example/order/enums/OrderType.java) |
| `ENUM-VALUE-PAY-RESULT-ERROR` | `enum-common.yaml` | enum | [PayResultCode.java](src/main/java/com/example/order/enums/PayResultCode.java) |
| `ENUM-VALUE-PAY-RESULT-SUCCESS` | `enum-common.yaml` | enum | [PayResultCode.java](src/main/java/com/example/order/enums/PayResultCode.java) |
| `ENUM-VALUE-PAYMENT-STATUS-INIT` | `enum-common.yaml` | enum | [PaymentStatus.java](src/main/java/com/example/order/enums/PaymentStatus.java) |
| `ENUM-VALUE-PAYMENT-STATUS-PROCESSING` | `enum-common.yaml` | enum | [PaymentStatus.java](src/main/java/com/example/order/enums/PaymentStatus.java) |
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
| `VIEW-ORDER-INFO` | `view-order.yaml` | view | [OrderInfo.java](src/main/java/com/example/order/view/OrderInfo.java) |
| `VIEW-USER-INFO` | `view-order.yaml` | view | [UserInfo.java](src/main/java/com/example/order/view/UserInfo.java) |
