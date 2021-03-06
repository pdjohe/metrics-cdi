/**
 * Copyright (C) 2013 Antonin Stefanutti (antonin.stefanutti@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.astefanutti.metrics.cdi;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.annotation.Counted;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundConstruct;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import javax.interceptor.AroundTimeout;

@Counted
@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE + 10)
/* package-private */ class CountedInterceptor {

    private final MetricRegistry registry;

    private final MetricResolver resolver;

    @Inject
    private CountedInterceptor(MetricRegistry registry, MetricResolver resolver) {
        this.registry = registry;
        this.resolver = resolver;
    }

    @AroundConstruct
    private Object countedConstructor(InvocationContext context) throws Exception {
        return countedCallable(context, context.getConstructor().getDeclaringClass(), context.getConstructor());
    }

    @AroundInvoke
    private Object countedMethod(InvocationContext context) throws Exception {
        return countedCallable(context, context.getTarget().getClass().getSuperclass(), context.getMethod());
    }

    @AroundTimeout
    private Object countedTimeout(InvocationContext context) throws Exception {
        return countedCallable(context, context.getTarget().getClass().getSuperclass(), context.getMethod());
    }

    private <E extends Member & AnnotatedElement> Object countedCallable(InvocationContext context, Class<?> topClass, E element) throws Exception {
        MetricResolver.Of<Counted> counted = resolver.counted(topClass, element);
        Counter counter = (Counter) registry.getMetrics().get(counted.metricName());
        if (counter == null)
            throw new IllegalStateException("No counter with name [" + counted.metricName() + "] found in registry [" + registry + "]");

        counter.inc();
        try {
            return context.proceed();
        } finally {
            if (!counted.metricAnnotation().monotonic())
                counter.dec();
        }
    }
}
