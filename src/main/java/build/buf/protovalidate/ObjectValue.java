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

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The {@link Value} type that contains a field descriptor and its value. */
final class ObjectValue implements Value {

  /**
   * {@link com.google.protobuf.Descriptors.FieldDescriptor} is the field descriptor for the value.
   */
  private final Descriptors.FieldDescriptor fieldDescriptor;

  /** Object type since the object type is inferred from the field descriptor. */
  private final Object value;

  /**
   * Constructs a new {@link ObjectValue}.
   *
   * @param fieldDescriptor The field descriptor for the value.
   * @param value The value associated with the field descriptor.
   */
  ObjectValue(Descriptors.FieldDescriptor fieldDescriptor, Object value) {
    this.fieldDescriptor = fieldDescriptor;
    this.value = value;
  }

  @Override
  public Descriptors.FieldDescriptor fieldDescriptor() {
    return fieldDescriptor;
  }

  @Nullable
  @Override
  public Message messageValue() {
    if (fieldDescriptor.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE) {
      return (Message) value;
    }
    return null;
  }

  @Override
  public <T> T value(Class<T> clazz) {
    return clazz.cast(ProtoAdapter.toCel(fieldDescriptor, value));
  }

  @Override
  public Object rawValue() {
    return value;
  }

  @Override
  public List<Value> repeatedValue() {
    List<Value> out = new ArrayList<>();
    if (fieldDescriptor.isRepeated()) {
      List<?> list = (List<?>) value;
      for (Object o : list) {
        out.add(new ListElementValue(fieldDescriptor, o));
      }
    }
    return out;
  }
}
