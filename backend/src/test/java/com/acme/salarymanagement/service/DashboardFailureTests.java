package com.acme.salarymanagement.service;

import com.acme.salarymanagement.exception.ApiRequestException;
import com.acme.salarymanagement.impl.DashboardServiceImpl;
import com.acme.salarymanagement.util.SqlUtility;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DashboardFailureTests {
    @Test
    void missingReferenceConfigurationIsUnavailableRatherThanAnEmptySuccess() {
        var service = new DashboardServiceImpl(mock(SqlUtility.class), Clock.systemUTC());
        assertThatThrownBy(service::referenceData).isInstanceOfSatisfying(ApiRequestException.class,
                error -> org.assertj.core.api.Assertions.assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void databaseFailureIsUnavailableForBothOperationsAndRetainsTheCause() {
        var sql = mock(SqlUtility.class);
        var cause = new DataAccessResourceFailureException("Database unavailable");
        doThrow(cause).when(sql).query(anyString(), any(), any(Object[].class));
        var service = new DashboardServiceImpl(sql, Clock.systemUTC());
        assertUnavailable(service::referenceData, cause);
        assertUnavailable(() -> service.compensation(Map.of(), List.of("currency")), cause);
    }

    private void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation, Exception cause) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(ApiRequestException.class, error -> {
            org.assertj.core.api.Assertions.assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            org.assertj.core.api.Assertions.assertThat(error.getCause()).isSameAs(cause);
        });
    }
}
