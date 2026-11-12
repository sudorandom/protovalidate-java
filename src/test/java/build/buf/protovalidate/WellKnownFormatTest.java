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

import com.google.re2j.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Checks the hand-written email, UUID, and trimmed UUID checks against the regexes they replace, on
 * edge cases and on random strings built from the characters that matter to each format.
 */
class WellKnownFormatTest {
  private static final Pattern EMAIL_REGEX =
      Pattern.compile(
          "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$");
  private static final Pattern UUID_REGEX =
      Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
  private static final Pattern TUUID_REGEX = Pattern.compile("^[0-9a-fA-F]{32}$");

  @Test
  void emailMatchesRegex() {
    List<String> inputs = new ArrayList<>();
    inputs.add("");
    inputs.add("@");
    inputs.add("a@");
    inputs.add("@b");
    inputs.add("a@b");
    inputs.add("a@b.");
    inputs.add("a@.b");
    inputs.add("a@b..c");
    inputs.add("a@-b");
    inputs.add("a@b-");
    inputs.add("a@b-c.d");
    inputs.add("a@@b");
    inputs.add("a@b@c");
    inputs.add("a.b+c@example.com");
    inputs.add("foo@example.com\n");
    inputs.add("é@example.com");
    inputs.add("a@exämple.com");
    inputs.add("a@" + label(63));
    inputs.add("a@" + label(64));
    inputs.add("a@" + label(63) + ".c");
    inputs.add("a@c." + label(64));
    Random random = new Random(1);
    String localChars = "aZ09.!#$%&'*+/=?^_`{|}~-@ é";
    String domainChars = "aZ09-.@_é";
    for (int i = 0; i < 20000; i++) {
      StringBuilder sb = new StringBuilder();
      appendRandom(sb, random, localChars, random.nextInt(4));
      if (random.nextInt(8) != 0) {
        sb.append('@');
      }
      int labels = random.nextInt(4);
      for (int j = 0; j < labels; j++) {
        if (j > 0 || random.nextInt(8) == 0) {
          sb.append('.');
        }
        // Mostly short labels, sometimes around the 63-character limit.
        int length = random.nextInt(4) == 0 ? 60 + random.nextInt(6) : random.nextInt(5);
        appendRandom(sb, random, domainChars, length);
      }
      inputs.add(sb.toString());
    }
    for (String input : inputs) {
      assertThat(CustomOverload.isEmail(input))
          .as("isEmail(%s)", input)
          .isEqualTo(EMAIL_REGEX.matches(input));
    }
  }

  @Test
  void uuidMatchesRegex() {
    List<String> inputs = new ArrayList<>();
    inputs.add("");
    inputs.add("00112233-4455-6677-8899-aabbccddeeff");
    inputs.add("00112233-4455-6677-8899-AABBCCDDEEFF");
    inputs.add("00112233-4455-6677-8899-aabbccddeef");
    inputs.add("00112233-4455-6677-8899-aabbccddeeffa");
    inputs.add("00112233-4455-6677-8899-aabbccddeefg");
    inputs.add("0011223-34455-6677-8899-aabbccddeeff");
    inputs.add("00112233445566778899aabbccddeeff");
    inputs.add("00112233445566778899aabbccddeef");
    inputs.add("00112233445566778899aabbccddeeff0");
    inputs.add("00112233445566778899aabbccddeefG");
    Random random = new Random(2);
    String chars = "09afAFgG-";
    for (int i = 0; i < 20000; i++) {
      StringBuilder sb = new StringBuilder();
      // Lengths around 32 and 36, with mostly well-placed hyphens.
      int length = (random.nextBoolean() ? 32 : 36) + random.nextInt(3) - 1;
      for (int j = 0; j < length; j++) {
        boolean hyphenSlot = length >= 36 && (j == 8 || j == 13 || j == 18 || j == 23);
        if (hyphenSlot && random.nextInt(16) != 0) {
          sb.append('-');
        } else if (random.nextInt(16) == 0) {
          sb.append(chars.charAt(random.nextInt(chars.length())));
        } else {
          sb.append("0123456789abcdefABCDEF".charAt(random.nextInt(22)));
        }
      }
      inputs.add(sb.toString());
    }
    for (String input : inputs) {
      assertThat(StringRulesEvaluator.WellKnownFormat.UUID.validate(input))
          .as("uuid(%s)", input)
          .isEqualTo(UUID_REGEX.matches(input));
      assertThat(StringRulesEvaluator.WellKnownFormat.TUUID.validate(input))
          .as("tuuid(%s)", input)
          .isEqualTo(TUUID_REGEX.matches(input));
    }
  }

  private static String label(int length) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; i++) {
      sb.append('b');
    }
    return sb.toString();
  }

  private static void appendRandom(StringBuilder sb, Random random, String chars, int length) {
    for (int i = 0; i < length; i++) {
      sb.append(chars.charAt(random.nextInt(chars.length())));
    }
  }
}
