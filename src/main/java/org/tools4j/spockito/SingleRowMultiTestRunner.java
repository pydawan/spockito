/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2017 tools4j.org (Marco Terzer)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.tools4j.spockito;

import org.junit.Test;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;

public class SingleRowMultiTestRunner extends BlockJUnit4ClassRunner {

    private final TableRow tableRow;
    private final ValueConverter defaultValueConverter;

    public SingleRowMultiTestRunner(final Class<?> clazz,
                                    final TableRow tableRow,
                                    final ValueConverter defaultValueConverter) throws InitializationError {
        super(clazz);
        this.tableRow = Objects.requireNonNull(tableRow);
        this.defaultValueConverter = Objects.requireNonNull(defaultValueConverter);
    }

    @Override
    protected Statement classBlock(RunNotifier notifier) {
        return childrenInvoker(notifier);
    }

    @Override
    protected Annotation[] getRunnerAnnotations() {
        return new Annotation[0];
    }

    @Override
    public Object createTest() throws Exception {
        final Object testInstance = createTestUsingConstructorInjection();
        return fieldsAreAnnotated() ? injectAnnotatedFields(testInstance) : testInstance;
    }

    private Object createTestUsingConstructorInjection() throws Exception {
        final Constructor<?> constructor = getTestClass().getOnlyConstructor();
        final ValueConverter valueConverter = Spockito.getValueConverter(constructor.getAnnotation(Spockito.UseValueConverter.class), defaultValueConverter);
        final Object[] args = tableRow.convertValues(constructor, valueConverter);
        return constructor.newInstance(args);
    }

    private Object injectAnnotatedFields(final Object testInstance) throws Exception {
        final List<FrameworkField> fields = getFieldsAnnotatedByRef();
        final Object[] fieldValues = tableRow.convertValues(fields, defaultValueConverter);
        for (int i = 0; i < fields.size(); i++) {
            final Field field = fields.get(i).getField();
            try {
                field.set(testInstance, fieldValues[i]);
            } catch (final Exception e) {
                throw new Exception(getTestClass().getName()
                        + ": Trying to set " + field.getName()
                        + " with the value " + fieldValues[i], e);
            }
        }
        return testInstance;
    }

    @Override
    protected String getName() {
        return Spockito.getName(getTestClass().getOnlyConstructor(), tableRow);
    }

    @Override
    protected void validateConstructor(List<Throwable> errors) {
        validateOnlyOneConstructor(errors);
        validateConstructorArgs(errors);
    }

    @Override
    protected void validateFields(List<Throwable> errors) {
        super.validateFields(errors);
        if (fieldsAreAnnotated()) {
            final List<FrameworkField> fields = getFieldsAnnotatedByRef();
            for (final FrameworkField field : fields) {
                final String refName = field.getField().getAnnotation(Spockito.Ref.class).value();
                if (!tableRow.isValidRefName(refName)) {
                    errors.add(new Exception("Invalid @Ref value: " + refName +
                            " does not reference a column of the table defined by @Unroll"));
                }
            }
        }
    }

    protected void validateConstructorArgs(List<Throwable> errors) {
        final Constructor<?> constructor = getTestClass().getOnlyConstructor();
        final java.lang.reflect.Parameter[] parameters = constructor.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            final String refName = Spockito.parameterRefNameOrNull(parameters[i]);
            if (refName != null && !tableRow.isValidRefName(refName)) {
                errors.add(new Exception("Invalid @Ref value or parameter name for argument " + i +
                        " of type " + parameters[i].getType() + " in the constructor: " + refName +
                        " does not reference a column of the table defined by @Unroll"));
            }
        }
    }

    @Override
    protected void validateTestMethods(final List<Throwable> errors) {
        final List<FrameworkMethod> methods = getTestClass().getAnnotatedMethods(Test.class);
        for (final FrameworkMethod method : methods) {
            final Spockito.Unroll unroll = method.getAnnotation(Spockito.Unroll.class);
            if (unroll == null) {
                method.validatePublicVoidNoArg(false, errors);
            } else {
                method.validatePublicVoid(false, errors);
                method.validateNoTypeParametersOnArgs(errors);
            }
        }
    }

    private List<FrameworkField> getFieldsAnnotatedByRef() {
        return getTestClass().getAnnotatedFields(Spockito.Ref.class);
    }

    private boolean fieldsAreAnnotated() {
        return !getFieldsAnnotatedByRef().isEmpty();
    }

}
