package com.project.notification.config;

import com.project.notification.model.Notification;
import com.project.notification.model.NotificationDelivery;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationIndexInitializerTest {

    @Mock private MongoTemplate mongoTemplate;
    @Mock private IndexOperations notificationIndexes;
    @Mock private IndexOperations deliveryIndexes;

    @Test
    void createsEffectiveUniqueIndexesForSourceEventsAndDeliveries() {
        when(mongoTemplate.indexOps(Notification.class)).thenReturn(notificationIndexes);
        when(mongoTemplate.indexOps(NotificationDelivery.class)).thenReturn(deliveryIndexes);

        new NotificationIndexInitializer(mongoTemplate).initialize();

        ArgumentCaptor<Index> notificationIndex = ArgumentCaptor.forClass(Index.class);
        ArgumentCaptor<Index> deliveryIndex = ArgumentCaptor.forClass(Index.class);
        verify(notificationIndexes, times(1)).ensureIndex(notificationIndex.capture());
        verify(deliveryIndexes, times(1)).ensureIndex(deliveryIndex.capture());

        assertThat(notificationIndex.getValue().getIndexKeys())
                .containsEntry("sourceEventId", 1);
        assertThat(notificationIndex.getValue().getIndexOptions())
                .containsEntry("unique", true)
                .containsEntry("sparse", true);
        assertThat(deliveryIndex.getValue().getIndexKeys())
                .containsEntry("notificationId", 1);
        assertThat(deliveryIndex.getValue().getIndexOptions())
                .containsEntry("unique", true);
    }
}
