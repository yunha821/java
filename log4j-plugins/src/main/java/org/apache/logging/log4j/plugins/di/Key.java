/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */

package org.apache.logging.log4j.plugins.di;

import org.apache.logging.log4j.plugins.Ordered;
import org.apache.logging.log4j.plugins.QualifierType;
import org.apache.logging.log4j.plugins.util.AnnotationUtil;
import org.apache.logging.log4j.plugins.util.TypeUtil;
import org.apache.logging.log4j.util.Strings;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Type with an optional {@link QualifierType} type, name, and namespace. Keys are used for binding to and looking up instance
 * factories via {@link Injector}.
 *
 * @param <T> type of key
 */
public class Key<T> {
    private final Type type;
    private final Class<T> rawType;
    private final Class<? extends Annotation> qualifierType;
    private final String name;
    private final String namespace;
    private final int order;
    private final int hashCode;
    private String toString;

    /**
     * Anonymous subclasses override this constructor to instantiate this Key based on the type given.
     * For example, to represent the equivalent of an annotated field {@code @Named("abc") Map<String, List<String>> field},
     * this constructor would be used like so:
     *
     * <pre>{@code
     * Key<Map<String, List<String>>> key = new @Named("abc") Key<>() {};
     * // or equivalently
     * var key = new @Named("abc") Key<Map<String, List<String>>>() {};
     * }</pre>
     */
    protected Key() {
        type = TypeUtil.getSuperclassTypeParameter(getClass());
        rawType = TypeUtil.cast(TypeUtil.getRawType(type));
        final AnnotatedType superclass = getClass().getAnnotatedSuperclass();
        final Annotation qualifier = AnnotationUtil.getMetaAnnotation(superclass, QualifierType.class);
        qualifierType = qualifier != null ? qualifier.annotationType() : null;
        name = Keys.getName(superclass);
        namespace = Keys.getNamespace(superclass);
        order = getOrder(superclass);
        hashCode = Objects.hash(type, qualifierType, name.toLowerCase(Locale.ROOT), namespace.toLowerCase(Locale.ROOT));
    }

    private Key(
            final Type type, final Class<T> rawType, final Class<? extends Annotation> qualifierType, final String name,
            final String namespace, final int order) {
        this.type = type;
        this.rawType = rawType;
        this.qualifierType = qualifierType;
        this.name = name;
        this.namespace = namespace;
        this.order = order;
        hashCode = Objects.hash(type, qualifierType, name.toLowerCase(Locale.ROOT), namespace.toLowerCase(Locale.ROOT));
    }

    public final Type getType() {
        return type;
    }

    public final Class<T> getRawType() {
        return rawType;
    }

    public final String getName() {
        return name;
    }

    public final String getNamespace() {
        return namespace;
    }

    public final int getOrder() {
        return order;
    }

    /**
     * Returns a new key using the provided name and the same type and qualifier type as this instance.
     */
    public final Key<T> withName(final String name) {
        return new Key<>(type, rawType, qualifierType, name, namespace, order);
    }

    public final Key<T> withNamespace(final String namespace) {
        return new Key<>(type, rawType, qualifierType, name, namespace, order);
    }

    /**
     * Returns a new key using the provided qualifier type and the same type and name as this instance.
     */
    public final Key<T> withQualifierType(final Class<? extends Annotation> qualifierType) {
        return new Key<>(type, rawType, qualifierType, name, namespace, order);
    }

    /**
     * If this key's type {@code T} is a subtype of {@code Supplier<P>} for some supplied type {@code P}, then this
     * returns a new key with that type argument along with the same name and qualifier type as this key.
     */
    public final <P> Key<P> getSuppliedType() {
        if (type instanceof ParameterizedType && Supplier.class.isAssignableFrom(rawType)) {
            return forQualifiedNamedType(qualifierType, name, ((ParameterizedType) type).getActualTypeArguments()[0], namespace,
                    order);
        }
        return null;
    }

    public final <P> Key<P> getParameterizedTypeArgument(final int arg) {
        if (type instanceof ParameterizedType) {
            return forQualifiedNamedType(qualifierType, name, ((ParameterizedType) type).getActualTypeArguments()[arg],
                    namespace, order);
        }
        return null;
    }

    @Override
    public final boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Key<?>)) {
            return false;
        }
        final Key<?> that = (Key<?>) o;
        return TypeUtil.isEqual(this.type, that.type) &&
                this.name.equalsIgnoreCase(that.name) &&
                this.namespace.equalsIgnoreCase(that.namespace) &&
                Objects.equals(this.qualifierType, that.qualifierType);
    }

    @Override
    public final int hashCode() {
        return hashCode;
    }

    @Override
    public final String toString() {
        String string = toString;
        if (string == null) {
            toString = string = String.format("Key{namespace='%s', name='%s', type=%s, qualifierType=%s}",
                    namespace, name, type.getTypeName(), qualifierType != null ? qualifierType.getSimpleName() : Strings.EMPTY);
        }
        return string;
    }

    /**
     * Creates a Key for the class.
     */
    public static <T> Key<T> forClass(final Class<T> clazz) {
        return forQualifiedNamedType(getQualifierType(clazz),
                Keys.getName(clazz),
                clazz,
                Keys.getNamespace(clazz),
                getOrder(clazz));
    }

    /**
     * Creates a Key for the return type of the method.
     */
    public static <T> Key<T> forMethod(final Method method) {
        return forQualifiedNamedType(getQualifierType(method),
                Keys.getName(method),
                method.getGenericReturnType(),
                Keys.getNamespace(method),
                getOrder(method));
    }

    /**
     * Creates a Key for the parameter.
     */
    public static <T> Key<T> forParameter(final Parameter parameter) {
        return forQualifiedNamedType(getQualifierType(parameter),
                Keys.getName(parameter),
                parameter.getParameterizedType(),
                Keys.getNamespace(parameter),
                0);
    }

    /**
     * Creates a Key for the field.
     */
    public static <T> Key<T> forField(final Field field) {
        return forQualifiedNamedType(getQualifierType(field),
                Keys.getName(field),
                field.getGenericType(),
                Keys.getNamespace(field),
                0);
    }

    private static <T> Key<T> forQualifiedNamedType(
            final Class<? extends Annotation> qualifierType, final String name, final Type type, final String namespace,
            final int order) {
        final Class<T> rawType = TypeUtil.cast(TypeUtil.getRawType(type));
        return new Key<>(type, rawType, qualifierType, name, namespace, order);
    }

    private static Class<? extends Annotation> getQualifierType(final AnnotatedElement element) {
        final Annotation qualifierAnnotation = AnnotationUtil.getMetaAnnotation(element, QualifierType.class);
        return qualifierAnnotation != null ? qualifierAnnotation.annotationType() : null;
    }

    private static int getOrder(final AnnotatedElement element) {
        final Ordered annotation = element.getAnnotation(Ordered.class);
        return annotation != null ? annotation.value() : 0;
    }
}
