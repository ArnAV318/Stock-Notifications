package com.projects.stockalerts.persistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class RuleRepository {

    private final DynamoDbTable<RuleItem> table;

    public RuleRepository(DynamoDbEnhancedClient enhanced,
                          @Value("${app.dynamodb.tableName}") String tableName) {
        this.table = enhanced.table(tableName, TableSchema.fromBean(RuleItem.class));
    }

    public void put(RuleItem item) {
        table.putItem(item);
    }

    public Optional<RuleItem> get(String pk, String sk) {
        RuleItem found = table.getItem(Key.builder().partitionValue(pk).sortValue(sk).build());
        return Optional.ofNullable(found);
    }

    public void delete(String pk, String sk) {
        table.deleteItem(Key.builder().partitionValue(pk).sortValue(sk).build());
    }

    public List<RuleItem> listByUserPk(String pk) {
        List<RuleItem> out = new ArrayList<>();
        QueryConditional qc = QueryConditional.keyEqualTo(Key.builder().partitionValue(pk).build());

        PageIterable<RuleItem> pages = PageIterable.create(table.query(r -> r.queryConditional(qc)));
        pages.items().forEach(out::add);

        return out;
    }

    // Optional, used later for notification engine
    public List<RuleItem> listByTicker(String ticker) {
        DynamoDbIndex<RuleItem> index = table.index("GSI1");
        String gsiPk = "TICKER#" + ticker;

        List<RuleItem> out = new ArrayList<>();
        QueryConditional qc = QueryConditional.keyEqualTo(Key.builder().partitionValue(gsiPk).build());

        PageIterable<RuleItem> pages = PageIterable.create(index.query(r -> r.queryConditional(qc)));
        pages.items().forEach(out::add);

        return out;
    }
}