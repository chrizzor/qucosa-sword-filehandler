/*
 * Copyright 2015 Saxon State and University Library Dresden (SLUB)
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

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Appender;
import org.apache.log4j.LogManager;
import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.XMLUnit;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.purl.sword.base.Deposit;
import org.purl.sword.base.ServiceDocument;
import org.purl.sword.server.fedora.baseExtensions.DepositCollection;
import org.purl.sword.server.fedora.fedoraObjects.Datastream;
import org.purl.sword.server.fedora.fedoraObjects.FedoraObject;
import org.purl.sword.server.fedora.fedoraObjects.FedoraRepository;
import org.purl.sword.server.fedora.utils.StartupListener;
import org.purl.sword.server.fedora.utils.XMLProperties;

import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.support.membermodification.MemberMatcher.method;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
        StartupListener.class,
        DefaultFileHandler.class,
        FedoraRepository.class,
        QucosaMETSFileHandler.class})
abstract class QucosaMETSFileHandler_AbstractTest {

    public static final String COLLECTION = "collection:test";
    public static final String CONTENT_MODEL = "info:fedora/qucosa:CModel";
    public static final String MEDIA_TYPE = "application/vnd.qucosa.mets+xml";
    public static final String METS_FILE_ADD_DS = "/mets_add_ds.xml";
    public static final String METS_FILE_ALLREFS = "/mets_all_references.xml";
    public static final String METS_FILE_BAD = "/mets_missing_mods.xml";
    public static final String METS_FILE_BAD2 = "/mets_invalid_file.xml";
    public static final String METS_FILE_CHECKSUM = "/mets_file_checksum.xml";
    public static final String METS_FILE_DELETE_DS = "/mets_delete_ds.xml";
    public static final String METS_FILE_FILEGROUPS = "/mets_download_filegroup.xml";
    public static final String METS_FILE_OK = "/mets_ok.xml";
    public static final String METS_FILE_UPDATE = "/mets_update.xml";
    public static final String METS_FILE_UPDATE_MD5 = "9a8d972d972eb799d989d0d2307c9822";
    public static final String METS_FILE_URL = "/mets_url_file.xml";
    public static final String METS_JUST_SLUBINFO = "/mets_just_slubinfo.xml";
    public static final String METS_JUST_SLUBINFO_WITHOUT_RIGHTS = "/mets_just_slubinfo_without_rights.xml";
    public static final String METS_NO_FLOCAT = "/mets_no_flocat.xml";
    public static final String METS_RELATIONSHIP_UPDATES = "/mets_relationship_update.xml";
    public static final String METS_WITH_RECORDSTATE = "/mets_with_recordstate.xml";
    public static final String NS_FEDORA_RELSEXT = "info:fedora/fedora-system:def/relations-external#";
    public static final String NS_FOAF = "http://xmlns.com/foaf/0.1/";
    public static final String NS_MODS_V3 = "http://www.loc.gov/mods/v3";
    public static final String NS_RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    public static final String NS_SLUB = "http://slub-dresden.de/";
    public static final String NS_XLINK = "http://www.w3.org/1999/xlink";
    public static final String SUBMITTER = "qucosa";
    public static final String USERNAME = "fedoraAdmin";

    static {
        XMLUnit.setXpathNamespaceContext(new SimpleNamespaceContext(
                new HashMap() {{
                    put("foaf", NS_FOAF);
                    put("mods", NS_MODS_V3);
                    put("rdf", NS_RDF);
                    put("rel", NS_FEDORA_RELSEXT);
                    put("slub", NS_SLUB);
                    put("xlink", NS_XLINK);
                }}
        ));
    }

    protected Appender mockAppender;
    protected FedoraRepository mockFedoraRepository;

    @Before
    public void setupLogging() {
        mockAppender = mock(Appender.class);
        LogManager.getRootLogger().addAppender(mockAppender);
    }

    @After
    public void teardownLogging() {
        LogManager.getRootLogger().removeAppender(mockAppender);
    }

    @Before
    public void ensureLocalProperties() {
        PowerMockito.mockStatic(StartupListener.class);
        when(StartupListener.getPropertiesLocation()).thenReturn(
                System.class.getResource("/properties.xml").getFile());
    }

    @Before
    public void setupFedoraRepositoryMock() throws Exception {
        mockFedoraRepository = mock(FedoraRepository.class);
        PowerMockito.whenNew(FedoraRepository.class)
                .withAnyArguments()
                .thenReturn(mockFedoraRepository);
        PowerMockito.replace(method(FedoraRepository.class, "mintPid")).with(
                new InvocationHandler() {
                    private int i = 1;

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        return "test:" + (i++);
                    }
                });
        when(mockFedoraRepository.mintPid()).thenCallRealMethod();
        when(mockFedoraRepository.connect()).thenReturn(mockFedoraRepository);
    }

    DepositCollection buildDeposit(String metsFileName) {
        return buildDeposit(System.class.getResourceAsStream(metsFileName));
    }

    DepositCollection buildDeposit(InputStream mets) {
        Deposit dp = new Deposit();
        dp.setContentType(MEDIA_TYPE);
        dp.setUsername(USERNAME);
        dp.setOnBehalfOf(SUBMITTER);
        dp.setFile(mets);
        return new DepositCollection(dp, COLLECTION);
    }

    DepositCollection buildDepositWithTempFile(String metsFileName, String uri) throws Exception {
        Document document = new SAXBuilder().build(
                System.class.getResourceAsStream(metsFileName));
        final Namespace METS = Namespace.getNamespace("mets", "http://www.loc.gov/METS/");
        final Namespace XLINK = Namespace.getNamespace("xlink", "http://www.w3.org/1999/xlink");
        XPath xp2 = XPath.newInstance("//mets:file[@ID='ATT-1']");
        xp2.addNamespace(METS);
        Element file1 = (Element) xp2.selectSingleNode(document);
        Element file2 = (Element) file1.clone();
        file2.setAttribute("ID", String.valueOf(UUID.randomUUID()));
        file2.getChild("FLocat", METS).setAttribute("href", uri, XLINK);
        file2.getChild("FLocat", METS).setAttribute("USE", "TEMPORARY");
        file1.getParentElement().addContent(file2);
        return buildDeposit(IOUtils.toInputStream(new XMLOutputter().outputString(document)));
    }

    ServiceDocument buildServiceDocument() throws Exception {
        return new XMLProperties().getServiceDocument("someUser");
    }

    Datastream getDatastream(String dsID, FedoraObject fedoraObject) {
        for (Datastream ds : fedoraObject.getDatastreams()) {
            if (ds.getId().equals(dsID)) return ds;
        }
        return null;
    }

    protected DepositCollection buildDeposit(String metsFileName, String depositID) {
        DepositCollection dp = buildDeposit(metsFileName);
        dp.setDepositID(depositID);
        return dp;
    }
}
