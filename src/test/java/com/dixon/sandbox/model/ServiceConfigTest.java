/**
 * Copyright (c) 2026 TNQ MEDIA
 *
 * Author: TNQ MEDIA
 * Git: github.com/xcoj027
 */
package com.dixon.sandbox.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import org.junit.jupiter.api.Test;

class ServiceConfigTest {

	@Test
	void rejectsHealthResultCapturedBeforeLifecycleOperation() {
		ServiceConfig service = service();
		long healthRevision = service.snapshotHealthRevision();

		long operationRevision = service.tryBeginLifecycleOperation(ServiceStatus.STARTING);
		service.setStatus(ServiceStatus.RUNNING);

		assertThat(service.applyHealthResult(healthRevision, false, -1)).isFalse();
		assertThat(service.getDisplayStatus()).isEqualTo(ServiceStatus.STARTING);

		service.finishLifecycleOperation(operationRevision);
		assertThat(service.getDisplayStatus()).isEqualTo(ServiceStatus.RUNNING);
	}

	@Test
	void rejectsCompetingLifecycleOperationUntilOwnerFinishes() {
		ServiceConfig service = service();
		long operationRevision = service.tryBeginLifecycleOperation(ServiceStatus.STARTING);

		assertThat(service.tryBeginLifecycleOperation(ServiceStatus.STOPPING)).isNegative();
		assertThat(service.snapshotHealthRevision()).isNegative();

		service.finishLifecycleOperation(operationRevision);
		assertThat(service.tryBeginLifecycleOperation(ServiceStatus.STOPPING)).isPositive();
	}

	private ServiceConfig service() {
		return new ServiceConfig("web", "Buyer Web", "Test service", ServiceCategory.WEB_APP, new File("."), "true", 0,
				null);
	}
}
