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
import java.util.ArrayList;
import java.util.List;

/** Performs validation on a {@link com.google.protobuf.Message}. */
final class MessageEvaluator implements Evaluator {
  /** List of {@link Evaluator}s that are applied to a message. */
  private final List<Evaluator> evaluators = new ArrayList<>();

  /**
   * A recursive message type references its own evaluator before that evaluator is complete. Until
   * then it must not look like a tautology, or the back-reference would be dropped for good.
   */
  private boolean building = true;

  /** Marks the evaluator as fully built, so {@link #tautology()} can report an accurate result. */
  void finishBuilding() {
    building = false;
  }

  @Override
  public boolean tautology() {
    if (building) {
      return false;
    }
    for (Evaluator evaluator : evaluators) {
      if (!evaluator.tautology()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public List<RuleViolation.Builder> evaluate(Value val, boolean failFast)
      throws ExecutionException {
    List<RuleViolation.Builder> allViolations = null;
    for (int i = 0; i < evaluators.size(); i++) {
      List<RuleViolation.Builder> violations = evaluators.get(i).evaluate(val, failFast);
      if (violations.isEmpty()) {
        continue;
      }
      if (failFast) {
        return violations;
      }
      if (allViolations == null) {
        allViolations = new ArrayList<>(violations.size());
      }
      allViolations.addAll(violations);
    }
    if (allViolations == null) {
      return RuleViolation.NO_VIOLATIONS;
    }
    return allViolations;
  }

  /**
   * Appends an {@link Evaluator} to the list of evaluators.
   *
   * @param eval The evaluator to append.
   */
  void append(Evaluator eval) {
    if (eval.tautology()) {
      return;
    }
    evaluators.add(eval);
  }
}
