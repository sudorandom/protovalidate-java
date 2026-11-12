// Copyright 2023-2026 Buf Technologies, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package build.buf.protovalidate;

import static org.assertj.core.api.Assertions.assertThat;

import build.buf.protovalidate.exceptions.ValidationException;
import com.example.noimports.validationtest.ExampleMapMinMax;
import com.example.noimports.validationtest.ExampleRepeatedMinMax;
import com.example.noimports.validationtest.ExampleRepeatedUnique;
import com.example.noimports.validationtest.RepeatedItemInt32Gt;
import com.example.noimports.validationtest.RepeatedItemStringMinLen;
import com.example.noimports.validationtest.RepeatedItemWrapperGt;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Message;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Validator-level tests for {@link RepeatedRulesEvaluator} and {@link MapRulesEvaluator}. */
class RepeatedAndMapRulesEvaluatorTest {

  private static Validator nativeValidator() {
    Config config = Config.newBuilder().setEnableNativeRules(true).build();
    return ValidatorFactory.newBuilder().withConfig(config).build();
  }

  @Test
  void repeatedMinItemsViolation() throws ValidationException {
    // 1 item, min_items=2.
    ExampleRepeatedMinMax msg = ExampleRepeatedMinMax.newBuilder().addVal(1).build();
    ValidationResult result = nativeValidator().validate(msg);
    assertThat(result.getViolations()).hasSize(1);
    build.buf.validate.Violation proto = result.getViolations().get(0).toProto();
    assertThat(proto.getRuleId()).isEqualTo("repeated.min_items");
    assertThat(proto.getMessage()).isEqualTo("must contain at least 2 item(s)");
  }

  @Test
  void repeatedMaxItemsViolation() throws ValidationException {
    // 6 items, max_items=5.
    ExampleRepeatedMinMax msg =
        ExampleRepeatedMinMax.newBuilder()
            .addVal(1)
            .addVal(2)
            .addVal(3)
            .addVal(4)
            .addVal(5)
            .addVal(6)
            .build();
    ValidationResult result = nativeValidator().validate(msg);
    assertThat(result.getViolations()).hasSize(1);
    build.buf.validate.Violation proto = result.getViolations().get(0).toProto();
    assertThat(proto.getRuleId()).isEqualTo("repeated.max_items");
    assertThat(proto.getMessage()).isEqualTo("must contain no more than 5 item(s)");
  }

  @Test
  void repeatedUniqueValid() throws ValidationException {
    ExampleRepeatedUnique msg =
        ExampleRepeatedUnique.newBuilder().addVal("a").addVal("b").addVal("c").build();
    assertThat(nativeValidator().validate(msg).isSuccess()).isTrue();
  }

  @Test
  void repeatedUniqueViolation() throws ValidationException {
    ExampleRepeatedUnique msg =
        ExampleRepeatedUnique.newBuilder().addVal("a").addVal("b").addVal("a").build();
    ValidationResult result = nativeValidator().validate(msg);
    assertThat(result.getViolations()).hasSize(1);
    build.buf.validate.Violation proto = result.getViolations().get(0).toProto();
    assertThat(proto.getRuleId()).isEqualTo("repeated.unique");
    assertThat(proto.getMessage()).isEqualTo("repeated value must contain unique items");
  }

  @Test
  void mapMinPairsViolation() throws ValidationException {
    // Empty map, min_pairs=1.
    ExampleMapMinMax msg = ExampleMapMinMax.newBuilder().build();
    ValidationResult result = nativeValidator().validate(msg);
    assertThat(result.getViolations()).hasSize(1);
    build.buf.validate.Violation proto = result.getViolations().get(0).toProto();
    assertThat(proto.getRuleId()).isEqualTo("map.min_pairs");
    assertThat(proto.getMessage()).isEqualTo("map must be at least 1 entries");
  }

  @Test
  void mapMaxPairsViolation() throws ValidationException {
    // 4 entries, max_pairs=3.
    ExampleMapMinMax msg =
        ExampleMapMinMax.newBuilder()
            .putVal("a", "1")
            .putVal("b", "2")
            .putVal("c", "3")
            .putVal("d", "4")
            .build();
    ValidationResult result = nativeValidator().validate(msg);
    assertThat(result.getViolations()).hasSize(1);
    build.buf.validate.Violation proto = result.getViolations().get(0).toProto();
    assertThat(proto.getRuleId()).isEqualTo("map.max_pairs");
    assertThat(proto.getMessage()).isEqualTo("map must be at most 3 entries");
  }

  @Test
  void nativeAndCelProduceEqualViolationProto() throws ValidationException {
    // Repeated unique violation in both modes.
    ExampleRepeatedUnique msg = ExampleRepeatedUnique.newBuilder().addVal("a").addVal("a").build();
    Validator nativeV = nativeValidator();
    Validator celV =
        ValidatorFactory.newBuilder()
            .withConfig(Config.newBuilder().setEnableNativeRules(false).build())
            .build();
    assertThat(nativeV.validate(msg).getViolations().get(0).toProto())
        .isEqualTo(celV.validate(msg).getViolations().get(0).toProto());
  }

  // Item rules are evaluated per element by the native evaluators; their violations must be
  // identical to the CEL ones: same verdict, same Violation protos, in the same order.

  @Test
  void repeatedItemInt32MatchesCel() throws ValidationException {
    assertItemParity(RepeatedItemInt32Gt.newBuilder().addVal(1).addVal(2).build());
    assertItemParity(RepeatedItemInt32Gt.newBuilder().addVal(1).addVal(0).addVal(-2).build());
    assertItemParity(RepeatedItemInt32Gt.getDefaultInstance());
  }

  @Test
  void repeatedItemStringMatchesCel() throws ValidationException {
    assertItemParity(RepeatedItemStringMinLen.newBuilder().addVal("ab").addVal("abc").build());
    assertItemParity(
        RepeatedItemStringMinLen.newBuilder().addVal("ab").addVal("x").addVal("").build());
  }

  @Test
  void repeatedItemWrapperMatchesCel() throws ValidationException {
    // The element is the wrapper message; it must be unwrapped before the int32 rule applies.
    assertItemParity(RepeatedItemWrapperGt.newBuilder().addVal(Int32Value.of(1)).build());
    assertItemParity(
        RepeatedItemWrapperGt.newBuilder()
            .addVal(Int32Value.of(1))
            .addVal(Int32Value.of(0))
            .build());
  }

  private static void assertItemParity(Message msg) throws ValidationException {
    Validator celValidator =
        ValidatorFactory.newBuilder()
            .withConfig(Config.newBuilder().setEnableNativeRules(false).build())
            .build();
    assertThat(toProtos(nativeValidator().validate(msg)))
        .containsExactlyElementsOf(toProtos(celValidator.validate(msg)));
  }

  private static List<build.buf.validate.Violation> toProtos(ValidationResult result) {
    return result.getViolations().stream().map(Violation::toProto).collect(Collectors.toList());
  }
}
