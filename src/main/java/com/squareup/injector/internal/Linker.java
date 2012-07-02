/*
 * Copyright (C) 2012 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.injector.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Links bindings to their dependencies.
 *
 * @author Jesse Wilson
 */
public final class Linker {
  private static final Object UNINITIALIZED = new Object();

  /** Bindings requiring a call to attach(). May contain deferred bindings. */
  private final Queue<Binding<?>> unattachedBindings = new LinkedList<Binding<?>>();

  /** True unless calls to requestBinding() were unable to satisfy the binding. */
  private boolean currentAttachSuccess = true;

  /** All errors encountered during injection. */
  private final List<String> errors = new ArrayList<String>();

  /** All of the injector's bindings. */
  private final Map<String, Binding<?>> bindings = new HashMap<String, Binding<?>>();

  /**
   * Adds the {@code @Provides} bindings from {@code modules}. There may not
   * be any duplicated bindings in {@code modules}, though multiple calls to
   * this method may contain duplicates: last installed wins.
   */
  public void installModules(Object[] modules) {
    for (Binding<?> binding : Modules.getBindings(modules).values()) {
      putBinding(binding);
    }
  }

  /**
   * Links the bindings in {@code bindings}, creating JIT bindings as necessary
   * to fill in the gaps. When this returns all bindings and their dependencies
   * will be attached.
   */
  public void link() {
    unattachedBindings.addAll(bindings.values());

    Binding binding;
    while ((binding = unattachedBindings.poll()) != null) {
      if (binding instanceof DeferredBinding) {
        if (getBinding(binding.key) != null) {
          continue; // A binding for this key has already been promoted.
        }
        try {
          Binding<?> jitBinding = createJitBinding((DeferredBinding<?>) binding);
          // Enqueue the JIT binding so its own dependencies can be linked.
          unattachedBindings.add(jitBinding);
          putBinding(jitBinding);
        } catch (Exception e) {
          addError(e.getMessage() + " required by " + binding.requiredBy);
          putBinding(new UnresolvedBinding<Object>(binding.requiredBy, binding.key));
        }
      } else {
        attachBinding(binding);
      }
    }

    if (!errors.isEmpty()) {
      StringBuilder message = new StringBuilder();
      message.append("Errors creating injector:");
      for (String error : errors) {
        message.append("\n  ").append(error);
      }
      throw new IllegalArgumentException(message.toString());
    }
  }

  /**
   * Creates a just-in-time binding for the key in {@code deferred}. The type of
   * binding to be created depends on the key's type:
   * <ul>
   *   <li>Injections of {@code Provider<Foo>} and {@code MembersInjector<Bar>}
   *       will delegate to the bindings of {@code Foo} and {@code Bar}
   *       respectively.
   *   <li>Injections of other types will use the injectable constructors of
   *       those classes.
   * </ul>
   */
  private Binding<?> createJitBinding(DeferredBinding<?> deferred) throws ClassNotFoundException {
    String delegateKey = Keys.getDelegateKey(deferred.key);
    if (delegateKey != null) {
      return new BuiltInBinding<Object>(deferred.key, deferred.requiredBy, delegateKey);
    }

    String className = Keys.getClassName(deferred.key);
    if (className != null && !Keys.isAnnotated(deferred.key)) {
      // Handle all other injections with constructor bindings.
      return ConstructorBinding.create(Class.forName(className));
    }

    throw new IllegalArgumentException("No binding for " + deferred.key);
  }

  /**
   * Attempts to attach {@code binding} to its dependencies. If any dependency
   * is not available, the attach will fail. We'll enqueue creation of that
   * dependency and retry the attachment later.
   */
  private void attachBinding(Binding binding) {
    currentAttachSuccess = true;
    binding.attach(this);
    if (!currentAttachSuccess) {
      unattachedBindings.add(binding);
    }
  }

  /**
   * Returns the binding if it exists immediately. Otherwise this returns
   * null. The injector will create that binding later and reattach the
   * caller's binding.
   */
  public Binding<?> requestBinding(String key, final Object requiredBy) {
    Binding<?> binding = getBinding(key);
    if (binding == null) {
      // We can't satisfy this binding. Make sure it'll work next time!
      unattachedBindings.add(new DeferredBinding<Object>(requiredBy, key));
      currentAttachSuccess = false;
    }
    return binding;
  }

  private Binding<?> getBinding(String key) {
    return bindings.get(key);
  }

  private <T> void putBinding(final Binding<T> binding) {
    Binding<T> toInsert = binding;
    if (binding.isSingleton()) {
      toInsert = new Binding<T>(binding.requiredBy, binding.key) {
        private Object onlyInstance = UNINITIALIZED;
        @Override public void attach(Linker linker) {
          binding.attach(linker);
        }
        @Override public void injectMembers(T t) {
          binding.injectMembers(t);
        }
        @SuppressWarnings("unchecked") // onlyInstance is either 'UNINITIALIZED' or a 'T'.
        @Override public T get() {
          if (onlyInstance == UNINITIALIZED) {
            onlyInstance = binding.get();
          }
          return (T) onlyInstance;
        }
        @Override public boolean isSingleton() {
          return binding.isSingleton();
        }
      };
    }

    bindings.put(toInsert.key, toInsert);
  }

  private void addError(String message) {
    errors.add(message);
  }

  private static class DeferredBinding<T> extends Binding<T> {
    private DeferredBinding(Object requiredBy, String key) {
      super(requiredBy, key);
    }
  }

  private static class UnresolvedBinding<T> extends Binding<T> {
    private UnresolvedBinding(Object definedBy, String key) {
      super(definedBy, key);
    }
  }
}
