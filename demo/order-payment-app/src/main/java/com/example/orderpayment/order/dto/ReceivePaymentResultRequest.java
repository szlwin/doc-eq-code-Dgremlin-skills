package com.example.orderpayment.order.dto;

import jakarta.validation.constraints.*;
import com.example.orderpayment.payment.enums.PayResultCode;

/** DEC API-ORDER-RECEIVE-PAYMENT-RESULT; YAML api-order.yaml#apis[id=API-ORDER-RECEIVE-PAYMENT-RESULT].request; XML api-order.xml api[name=receivePaymentResult]/request. */
public class ReceivePaymentResultRequest {
    /** DEC API-PARAM-PAY-RESULT-CODE; YAML api-order.yaml#apis[id=API-ORDER-RECEIVE-PAYMENT-RESULT].request.params[id=API-PARAM-PAY-RESULT-CODE]; XML api-order.xml parameter[name=resultCode]. */
    @NotNull(message = "resultCode不能为空")
    private PayResultCode resultCode;

    public PayResultCode getResultCode() {
        return resultCode;
    }

    public void setResultCode(PayResultCode resultCode) {
        this.resultCode = resultCode;
    }
}
