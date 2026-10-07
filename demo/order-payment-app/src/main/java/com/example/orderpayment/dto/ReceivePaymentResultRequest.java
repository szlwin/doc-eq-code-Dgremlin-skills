package com.example.orderpayment.dto;

import jakarta.validation.constraints.*;
import com.example.orderpayment.enums.PayResultCode;

/** DEC API-ORDER-RECEIVE-PAYMENT-RESULT from api-order.yaml. */
public class ReceivePaymentResultRequest {
    /** DEC API-PARAM-PAY-RESULT-CODE. */
    @NotNull
    private PayResultCode resultCode;

    public PayResultCode getResultCode() {
        return resultCode;
    }

    public void setResultCode(PayResultCode resultCode) {
        this.resultCode = resultCode;
    }
}
