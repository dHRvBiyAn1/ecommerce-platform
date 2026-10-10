package com.project.common.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

class RecordMappingTest {
  private final BoundaryMapper mapper = Mappers.getMapper(BoundaryMapper.class);

  @Test
  void mapsLombokBuilderToRecordAndBackWithoutLosingDecimalPrecision() {
    UUID id = UUID.randomUUID();
    BigDecimal amount = new BigDecimal("123456789.123456789");
    BoundaryRecord record = mapper.toRecord(LombokBoundary.builder().id(id).amount(amount).build());
    assertThat(record).isEqualTo(new BoundaryRecord(id, amount));
    LombokBoundary bean = mapper.toBean(record);
    assertThat(bean.getId()).isEqualTo(id);
    assertThat(bean.getAmount()).isEqualTo(amount);
    assertThat(mapper.toRecord(null)).isNull();
    assertThat(mapper.toBean(null)).isNull();
  }

  public record BoundaryRecord(UUID id, BigDecimal amount) {}

  @Getter
  @Builder
  public static class LombokBoundary {
    private UUID id;
    private BigDecimal amount;
  }

  @Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
  public interface BoundaryMapper {
    BoundaryRecord toRecord(LombokBoundary value);

    LombokBoundary toBean(BoundaryRecord value);
  }
}
