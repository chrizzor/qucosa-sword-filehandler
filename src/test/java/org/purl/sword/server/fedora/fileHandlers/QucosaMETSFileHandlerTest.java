/*
 * Copyright 2014 Saxon State and University Library Dresden (SLUB)
 *
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
 */

package org.purl.sword.server.fedora.fileHandlers;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.purl.sword.server.fedora.utils.StartupListener;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(StartupListener.class)
public class QucosaMETSFileHandlerTest {

    @BeforeClass
    public static void setupStaticMock() {
        PowerMockito.mockStatic(StartupListener.class);
        when(StartupListener.getPropertiesLocation()).thenReturn(
                System.class.getResource("/properties.xml").getFile());
    }

    @Test
    public void handlesQucosaMETS() {
        FileHandler fh = new QucosaMETSFileHandler();

        assertTrue(fh.isHandled("application/vnd.qucosa.mets+xml", ""));
    }

}