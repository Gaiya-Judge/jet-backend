package com.kad.jet.report.service;

import com.kad.jet.report.dto.*;
import com.kad.jet.user.model._User;

import java.util.UUID;

public interface ReportService {

    PaymentSummaryReport getPaymentSummary(
            _User currentUser, UUID semesterId);

    OutstandingBalanceReport getOutstandingBalances(
            _User currentUser);

    RegistrationSummaryReport getRegistrationSummary(
            _User currentUser, UUID semesterId);

    WaiverReport getWaiverReport(_User currentUser);

    CarryoverStudentReport getCarryoverReport(
            _User currentUser, UUID semesterId);
}
