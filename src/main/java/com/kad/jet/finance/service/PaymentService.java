package com.kad.jet.finance.service;

import com.kad.jet.finance.dto.*;
import com.kad.jet.user.model._User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PaymentService {

    InitiatePaymentResponse initiatePayment(_User currentUser, InitiatePaymentRequest request);

    void handleWebhook(String paystackSignature, String rawPayload);

    PaymentResponse submitBankTransfer(_User currentUser, BankTransferRequest request);

    PaymentResponse verifyBankTransfer(_User currentUser, UUID paymentId, VerifyPaymentRequest request);

    PaymentResponse rejectBankTransfer(_User currentUser, UUID paymentId, RejectPaymentRequest request);

    TuitionAccountResponse getTuitionAccount(_User currentUser, UUID tuitionAccountId);

    Page<PaymentResponse> getPendingBankTransfers(_User currentUser, Pageable pageable);
}
