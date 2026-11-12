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

import build.buf.protovalidate.exceptions.ExecutionException;
import build.buf.validate.FieldPath;
import build.buf.validate.FieldPathElement;
import build.buf.validate.FieldRules;
import build.buf.validate.MapRules;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Performs validation on a map field's key-value pairs. */
final class MapEvaluator implements Evaluator {
  /** Rule path to map key rules */
  private static final FieldPath MAP_KEYS_RULE_PATH =
      FieldPath.newBuilder()
          .addElements(
              FieldPathUtils.fieldPathElement(
                  FieldRules.getDescriptor().findFieldByNumber(FieldRules.MAP_FIELD_NUMBER)))
          .addElements(
              FieldPathUtils.fieldPathElement(
                  MapRules.getDescriptor().findFieldByNumber(MapRules.KEYS_FIELD_NUMBER)))
          .build();

  /** Rule path to map value rules */
  private static final FieldPath MAP_VALUES_RULE_PATH =
      FieldPath.newBuilder()
          .addElements(
              FieldPathUtils.fieldPathElement(
                  FieldRules.getDescriptor().findFieldByNumber(FieldRules.MAP_FIELD_NUMBER)))
          .addElements(
              FieldPathUtils.fieldPathElement(
                  MapRules.getDescriptor().findFieldByNumber(MapRules.VALUES_FIELD_NUMBER)))
          .build();

  private final RuleViolationHelper helper;

  /** Rule for checking the map keys */
  private final ValueEvaluator keyEvaluator;

  /** Rule for checking the map values */
  private final ValueEvaluator valueEvaluator;

  /** Field descriptor of the map field */
  final Descriptors.FieldDescriptor fieldDescriptor;

  /** Field descriptor of the map key field */
  final Descriptors.FieldDescriptor keyFieldDescriptor;

  /** Field descriptor of the map value field */
  final Descriptors.FieldDescriptor valueFieldDescriptor;

  /**
   * Constructs a {@link MapEvaluator}.
   *
   * @param valueEvaluator The value evaluator this rule exists under.
   */
  MapEvaluator(ValueEvaluator valueEvaluator, Descriptors.FieldDescriptor fieldDescriptor) {
    this.helper = new RuleViolationHelper(valueEvaluator);
    this.keyEvaluator = new ValueEvaluator(null, MAP_KEYS_RULE_PATH);
    this.valueEvaluator = new ValueEvaluator(null, MAP_VALUES_RULE_PATH);
    this.fieldDescriptor = fieldDescriptor;
    this.keyFieldDescriptor = fieldDescriptor.getMessageType().findFieldByNumber(1);
    this.valueFieldDescriptor = fieldDescriptor.getMessageType().findFieldByNumber(2);
  }

  /**
   * Gets the key evaluator associated with this map evaluator.
   *
   * @return The key evaluator.
   */
  ValueEvaluator getKeyEvaluator() {
    return keyEvaluator;
  }

  /**
   * Gets the value evaluator associated with this map evaluator.
   *
   * @return The value evaluator.
   */
  ValueEvaluator getValueEvaluator() {
    return valueEvaluator;
  }

  @Override
  public boolean tautology() {
    return keyEvaluator.tautology() && valueEvaluator.tautology();
  }

  @Override
  public List<RuleViolation.Builder> evaluate(Value val, boolean failFast)
      throws ExecutionException {
    // Walk the entry messages directly, in entry order.
    List<?> entries = (List<?>) val.rawValue();
    List<RuleViolation.Builder> violations = null;
    for (int i = 0; i < entries.size(); i++) {
      Message entry = (Message) entries.get(i);
      List<RuleViolation.Builder> pairViolations =
          evalPairs(
              new ObjectValue(keyFieldDescriptor, entry.getField(keyFieldDescriptor)),
              new ObjectValue(valueFieldDescriptor, entry.getField(valueFieldDescriptor)),
              failFast);
      if (pairViolations.isEmpty()) {
        continue;
      }
      if (failFast) {
        return pairViolations;
      }
      if (violations == null) {
        violations = new ArrayList<>(pairViolations.size());
      }
      violations.addAll(pairViolations);
    }
    if (violations == null) {
      return RuleViolation.NO_VIOLATIONS;
    }
    return violations;
  }

  private List<RuleViolation.Builder> evalPairs(Value key, Value value, boolean failFast)
      throws ExecutionException {
    List<RuleViolation.Builder> keyViolations = keyEvaluator.evaluate(key, failFast);
    final List<RuleViolation.Builder> valueViolations;
    if (failFast && !keyViolations.isEmpty()) {
      // Don't evaluate value rules if failFast is enabled and keys failed validation.
      // We still need to continue execution to the end to properly prefix violation field paths.
      valueViolations = RuleViolation.NO_VIOLATIONS;
    } else {
      valueViolations = valueEvaluator.evaluate(value, failFast);
    }
    if (keyViolations.isEmpty() && valueViolations.isEmpty()) {
      return RuleViolation.NO_VIOLATIONS;
    }
    List<RuleViolation.Builder> violations =
        new ArrayList<>(keyViolations.size() + valueViolations.size());
    for (RuleViolation.Builder violation : keyViolations) {
      violations.add(violation.setForKey(true));
    }
    violations.addAll(valueViolations);

    FieldPathElement.Builder fieldPathElementBuilder =
        Objects.requireNonNull(helper.getFieldPathElement()).toBuilder();
    fieldPathElementBuilder.setKeyType(keyFieldDescriptor.getType().toProto());
    fieldPathElementBuilder.setValueType(valueFieldDescriptor.getType().toProto());
    switch (keyFieldDescriptor.getType().toProto()) {
      case TYPE_INT64:
      case TYPE_INT32:
      case TYPE_SINT32:
      case TYPE_SINT64:
      case TYPE_SFIXED32:
      case TYPE_SFIXED64:
        fieldPathElementBuilder.setIntKey(key.value(Number.class).longValue());
        break;
      case TYPE_UINT32:
      case TYPE_UINT64:
      case TYPE_FIXED32:
      case TYPE_FIXED64:
        fieldPathElementBuilder.setUintKey(key.value(Number.class).longValue());
        break;
      case TYPE_BOOL:
        fieldPathElementBuilder.setBoolKey(key.value(Boolean.class));
        break;
      case TYPE_STRING:
        fieldPathElementBuilder.setStringKey(key.value(String.class));
        break;
      default:
        throw new ExecutionException("Unexpected map key type");
    }
    FieldPathElement fieldPathElement = fieldPathElementBuilder.build();
    return FieldPathUtils.updatePaths(violations, fieldPathElement, helper.getRulePrefixElements());
  }
}
