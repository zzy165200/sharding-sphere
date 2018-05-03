/*
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingjdbc.dbtest;

import io.shardingjdbc.dbtest.asserts.AssertEngine;
import io.shardingjdbc.dbtest.config.AnalyzeConfig;
import io.shardingjdbc.dbtest.config.bean.AssertDDLDefinition;
import io.shardingjdbc.dbtest.config.bean.AssertDMLDefinition;
import io.shardingjdbc.dbtest.config.bean.AssertDQLDefinition;
import io.shardingjdbc.dbtest.config.bean.AssertDefinition;
import io.shardingjdbc.dbtest.config.bean.AssertsDefinition;
import io.shardingjdbc.dbtest.exception.DbTestException;
import io.shardingjdbc.dbtest.init.InItCreateSchema;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertNotNull;

@RunWith(Parameterized.class)
@RequiredArgsConstructor
public final class StartTest {
    
    private static final String INTEGRATION_RESOURCES_PATH = "asserts";
    
    private static boolean isInitialized = IntegrateTestEnvironment.getInstance().isInitialized();
    
    private static boolean isCleaned = IntegrateTestEnvironment.getInstance().isInitialized();
    
    private static final List<String[]> RESULT_ASSERT = new ArrayList<>();
    
    private final String path;
    
    private final String id;
    
    @Parameters(name = "{0} ({2}) -> {1}")
    public static Collection<String[]> getParameters() throws IOException, JAXBException {
        URL integrateResources = StartTest.class.getClassLoader().getResource(INTEGRATION_RESOURCES_PATH);
        assertNotNull(integrateResources);
        for (String each : getAssertFiles(integrateResources)) {
            AssertsDefinition assertsDefinition = AnalyzeConfig.analyze(each);
            if (StringUtils.isNotBlank(assertsDefinition.getBaseConfig())) {
                String[] dbs = StringUtils.split(assertsDefinition.getBaseConfig(), ",");
                for (String db : dbs) {
                    InItCreateSchema.addDatabase(db);
                }
            } else {
                for (String db : AssertEngine.DEFAULT_DATABASES) {
                    InItCreateSchema.addDatabase(db);
                }
            }
            List<AssertDQLDefinition> assertDQLs = assertsDefinition.getAssertDQL();
            collateData(RESULT_ASSERT, each, assertDQLs);
            List<AssertDMLDefinition> assertDMLs = assertsDefinition.getAssertDML();
            collateData(RESULT_ASSERT, each, assertDMLs);
            List<AssertDDLDefinition> assertDDLs = assertsDefinition.getAssertDDL();
            collateData(RESULT_ASSERT, each, assertDDLs);
            AssertEngine.addAssertDefinition(each, assertsDefinition);
        }
        return RESULT_ASSERT;
    }
    
    private static List<String> getAssertFiles(final URL integrateResources) throws IOException {
        final List<String> result = new LinkedList<>();
        Files.walkFileTree(Paths.get(integrateResources.getPath()), new SimpleFileVisitor<Path>() {
            
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes basicFileAttributes) {
                if (file.getFileName().toString().startsWith("assert-") && file.getFileName().toString().endsWith(".xml")) {
                    result.add(file.toFile().getPath());
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }
    
    private static <T extends AssertDefinition> void collateData(final List<String[]> result, final String path, final List<T> asserts) {
        if (asserts == null) {
            return;
        }
        List<String> assertDefinitions = new ArrayList<>(asserts.size());
        for (AssertDefinition each : asserts) {
            if (assertDefinitions.contains(each.getId())) {
                throw new DbTestException("ID can't be repeated");
            }
            assertDefinitions.add(each.getId());
            result.add(new String[]{path, each.getId()});
        }
    }
    
    @BeforeClass
    public static void beforeClass() {
        if (isInitialized) {
            InItCreateSchema.createDatabase();
            InItCreateSchema.createTable();
            isInitialized = false;
        } else {
            InItCreateSchema.dropDatabase();
            InItCreateSchema.createDatabase();
            InItCreateSchema.createTable();
        }
    }
    
    @Test
    public void test() {
        AssertEngine.runAssert(path, id);
    }
    
    @AfterClass
    public static void afterClass() {
        if (isCleaned) {
            InItCreateSchema.dropDatabase();
            isCleaned = false;
        }
    }
}