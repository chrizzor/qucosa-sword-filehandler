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

import org.apache.log4j.Logger;
import org.jdom.JDOMException;
import org.purl.sword.base.SWORDEntry;
import org.purl.sword.base.SWORDException;
import org.purl.sword.base.ServiceDocument;
import org.purl.sword.server.fedora.baseExtensions.DepositCollection;
import org.purl.sword.server.fedora.fedoraObjects.*;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static org.purl.sword.server.fedora.fedoraObjects.State.DELETED;

public class QucosaMETSFileHandler extends DefaultFileHandler {

    private static final Logger log = Logger.getLogger(QucosaMETSFileHandler.class);

    private static final String DEFAULT_COLLECTION_PID = "qucosa:all";
    public static final String QUCOSA_CMODEL = "qucosa:CModel";

    public QucosaMETSFileHandler() {
        super("application/vnd.qucosa.mets+xml", "");
    }

    @Override
    public SWORDEntry ingestDeposit(DepositCollection deposit, ServiceDocument serviceDocument) throws SWORDException {
        METS mets = loadAndValidate(deposit);
        final FedoraRepository repository = connectRepository(deposit);
        final String pid = obtainPID(deposit, repository);
        final FedoraObject fedoraObject = new FedoraObject(pid);

        List<Datastream> datastreams;
        datastreams = mets.getDatastreams();
        ensureValidDSIds(datastreams);

        fedoraObject.setIdentifiers(getIdentifiers(deposit));
        fedoraObject.setRelsext(buildRelationships(deposit, mets));
        fedoraObject.setDatastreams(datastreams);
        fedoraObject.setDc(mets.getDublinCore());

        validateObject(fedoraObject);
        final SWORDEntry swordEntry = getSWORDEntry(deposit, serviceDocument, fedoraObject);

        if (!deposit.isNoOp()) { // Don't ingest if no-op is set
            repository.ingest(fedoraObject);
            delete(mets.getTemporayFiles());
        }

        return swordEntry;
    }

    /**
     * Use deposit information to update an existing Fedora object.
     * <p/>
     * Content of the given deposit must be a METS XML file, just like for ingestDeposit().
     * Not all of the METS sections have to be present. Special information about the kind
     * of update can be encoded in METS attributes.
     * <p/>
     * 1. Sections that are not present are not modified.
     * <p/>
     * 2. If a <mets:mdWrap MDTYPE="MODS"> element is present in <mets:dmdSec> the MODS
     * datastream gets replaced with XML within <mets:xmlData>. MODS datastream cannot be
     * deleted.
     * <p/>
     * 3. To delete datastream information an explicit "DELETE" state has to be encoded in
     * the USE attribute of file sections. The datastream will not be removed from the
     * repository, instead it's state will change to DELETED.
     * <p/>
     * 4. To add a new datastream a new <mets:file> with an unused ID has to be present, holding
     * an <mets:FLocat> element describing the upload.
     * <p/>
     * 5. To replace an existing datastream with a newer version a <mets:file> element has to be
     * present, having an ID value equal to the DSID of an existing datastream.
     * <p/>
     * All datastream actions apply to the object PID specified in the deposit.
     *
     * @param deposit         The deposit
     * @param serviceDocument The service document
     * @return SWORDEntry with deposit result and links.
     * @throws SWORDException if something goes wrong
     */
    @Override
    public SWORDEntry updateDeposit(DepositCollection deposit, ServiceDocument serviceDocument) throws SWORDException {
        METS mets = loadAndValidate(deposit);
        final FedoraRepository repository = connectRepository(deposit);
        final String pid = deposit.getDepositID();
        final FedoraObject fedoraObject = new FedoraObject(pid);

        fedoraObject.setDc(new DublinCore());

        final SWORDEntry swordEntry = getSWORDEntry(deposit, serviceDocument, fedoraObject);

        if (!deposit.isNoOp()) { // Don't ingest if no-op is set
            updateIfPresent(repository, pid, mets.getModsDatastream());
            updateAttachmentDatastreams(repository, pid, mets.getFileDatastreams());
            updateOrAdd(repository, pid, mets.getSlubInfoDatastream());
            updateOrAdd(repository, pid, mets.getQucosaXmlDatastream());
            delete(mets.getTemporayFiles());
        }

        return swordEntry;
    }

    private METS loadAndValidate(DepositCollection deposit) throws SWORDException {
        validateDeposit(deposit);
        METS mets = loadMets(deposit);
        assertChecksum(deposit, mets);
        return mets;
    }


    @Override
    public void validateObject(FedoraObject fedoraObject) throws SWORDException {
        // ensure there is a MODS datastream
        boolean modsExists = false;
        for (Datastream ds : fedoraObject.getDatastreams()) {
            if (ds.getId().equals("MODS")) {
                modsExists = true;
                break;
            }
        }
        if (!modsExists) throw new SWORDException("Missing MODS datastream in METS source");
    }

    private Relationship buildRelationships(DepositCollection deposit, METS mets) {
        Relationship rels = super.getRelationships(deposit);

        rels.addModel("info:fedora/" + QUCOSA_CMODEL);

        String collectionPid = deposit.getCollectionPid();
        if (collectionPid == null || collectionPid.isEmpty()) {
            collectionPid = DEFAULT_COLLECTION_PID;
        }
        rels.add("isMemberOfCollection", collectionPid);

        return rels;
    }

    private FedoraRepository connectRepository(DepositCollection deposit) throws SWORDException {
        final FedoraRepository repo = new FedoraRepository(this._props, deposit.getUsername(), deposit.getPassword());
        return repo.connect();
    }

    private SWORDException swordException(String message, Exception e) {
        return new SWORDException(message, e);
    }

    private void validateDeposit(DepositCollection pDeposit) {
        if (pDeposit.getOnBehalfOf() == null || pDeposit.getOnBehalfOf().isEmpty()) {
            log.warn("X-On-Behalf-Of header is not set. HTTP request principal will be used as repository object owner ID.");
        }
    }

    private void assertChecksum(DepositCollection deposit, METS mets) throws SWORDException {
        if (hasMd5(deposit)) {
            final String depositMd5 = deposit.getMd5();
            final String metsMd5 = mets.getMd5();
            if (!metsMd5.equals(depositMd5)) {
                throw new SWORDException("Bad MD5 for submitted content: " + metsMd5 + ". Expected: " + depositMd5);
            }
        }
    }

    private METS loadMets(DepositCollection deposit) throws SWORDException {
        METS mets;
        try {
            mets = new METS(deposit.getFile());
        } catch (NoSuchAlgorithmException e) {
            throw swordException("No MD5 digest algorithm found", e);
        } catch (JDOMException | IOException e) {
            throw swordException("Couldn't build METS from deposit", e);
        }
        return mets;
    }

    private String obtainPID(DepositCollection deposit, FedoraRepository repository) throws SWORDException {
        String pid = "noop:nopid";
        if (isSet(deposit.getSlug())) {
            pid = deposit.getSlug();
        } else if (!deposit.isNoOp()) {
            // Don't mint PID if no op is set
            pid = repository.mintPid();
        }
        return pid;
    }

    private boolean isSet(String s) {
        return !emptyIfNull(s).isEmpty();
    }

    private void delete(List<File> files) {
        for (File f : files) {
            if (!f.delete()) {
                log.warn("Unsuccessful delete attempt for " + f.getAbsolutePath());
            }
        }
    }

    private String emptyIfNull(String s) {
        return (s == null) ? "" : s;
    }

    private boolean hasMd5(DepositCollection deposit) {
        return (deposit.getMd5() != null) && (!deposit.getMd5().isEmpty());
    }

    private void updateAttachmentDatastreams(FedoraRepository repository, String pid, List<Datastream> datastreams) throws SWORDException {
        for (Datastream attDatastream : datastreams) {
            if (attDatastream instanceof VoidDatastream) {
                if (repository.hasDatastream(pid, attDatastream.getId())) {
                    repository.setDatastreamState(pid, attDatastream.getId(), DELETED, null);
                }
            } else {
                if (repository.hasDatastream(pid, attDatastream.getId())) {
                    repository.modifyDatastream(pid, attDatastream, null);
                } else {
                    repository.addDatastream(pid, attDatastream, null);
                }
            }
        }
    }

    private void updateIfPresent(FedoraRepository repository, String pid, Datastream datastream) throws SWORDException {
        if (datastream != null) {
            if (repository.hasDatastream(pid, datastream.getId())) {
                repository.modifyDatastream(pid, datastream, null);
            }
        }
    }

    private void updateOrAdd(FedoraRepository repository, String pid, Datastream datastream) throws SWORDException {
        if (datastream != null) {
            if (repository.hasDatastream(pid, datastream.getId())) {
                repository.modifyDatastream(pid, datastream, null);
            } else {
                repository.addDatastream(pid, datastream, null);
            }
        }
    }

}
