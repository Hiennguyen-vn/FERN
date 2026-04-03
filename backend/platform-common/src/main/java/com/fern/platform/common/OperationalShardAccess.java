package com.fern.platform.common;

import java.util.Objects;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public record OperationalShardAccess(
        ShardId shardId,
        NamedParameterJdbcTemplate jdbc,
        TransactionTemplate tx
) {
    public OperationalShardAccess {
        Objects.requireNonNull(shardId, "shardId");
        Objects.requireNonNull(jdbc, "jdbc");
        Objects.requireNonNull(tx, "tx");
    }
}
