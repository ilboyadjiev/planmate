package com.planmate.util;

import java.sql.Timestamp;
import java.time.Instant;

import com.planmate.dto.PlanMateEntity;

public class EntityUtil {

	public static void fillAbstractEntityAttributes(PlanMateEntity entity) {
		entity.setCreationTime(Timestamp.from(Instant.now()));
		entity.setCreatedBy("unknown");
		entity.setUpdateTime(Timestamp.from(Instant.now()));
		entity.setUpdatedBy("unknown");
		entity.setDeleted(false);
	}
}
