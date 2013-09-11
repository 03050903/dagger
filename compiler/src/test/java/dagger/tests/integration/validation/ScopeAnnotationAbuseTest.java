/**
 * Copyright (c) 2013 Google, Inc.
 * Copyright (c) 2013 Square, Inc.
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
package dagger.tests.integration.validation;

import com.google.common.base.Joiner;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static dagger.tests.integration.ProcessorTestUtils.daggerProcessors;
import static org.truth0.Truth.ASSERT;

@RunWith(JUnit4.class)
public class ScopeAnnotationAbuseTest {
  private static final String ABSTRACTION_SCOPING_TEXT =
      "Scoping annotations are only allowed on concrete types and @Provides methods:";

  @Test public void compileFailsWithScopeOnInterface() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", Joiner.on("\n").join(
        "import dagger.Module;",
        "import javax.inject.Singleton;",
        "class Test {",
        "  @Module(library = true, injects = Interface.class) class TestModule { }",
        "  @Singleton interface Interface { }",
        "}"));

    ASSERT.about(javaSource())
        .that(sourceFile).processedWith(daggerProcessors()).failsToCompile()
        .withErrorContaining(ABSTRACTION_SCOPING_TEXT).in(sourceFile).onLine(5).atColumn(14).and()
        .withErrorContaining("Test.Interface").in(sourceFile).onLine(5).atColumn(14);
  }

  @Test public void compileFailsWithScopeOnAbstractClass() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", Joiner.on("\n").join(
        "import dagger.Module;",
        "import javax.inject.Singleton;",
        "class Test {",
        "  @Module(library = true, injects = AbstractClass.class) class TestModule { }",
        "  @Singleton abstract class AbstractClass { }",
        "}"));

    ASSERT.about(javaSource())
        .that(sourceFile).processedWith(daggerProcessors()).failsToCompile()
        .withErrorContaining(ABSTRACTION_SCOPING_TEXT).in(sourceFile).onLine(5).atColumn(23).and()
        .withErrorContaining("Test.AbstractClass").in(sourceFile).onLine(5).atColumn(23);
  }
  
  
}
