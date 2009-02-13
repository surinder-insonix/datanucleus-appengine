// Copyright 2008 Google Inc. All Rights Reserved.
package org.datanucleus.store.appengine;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

import junit.framework.TestCase;

import org.datanucleus.jdo.exceptions.TransactionNotReadableException;
import org.datanucleus.jdo.exceptions.TransactionNotWritableException;
import org.datanucleus.test.Flight;
import org.easymock.EasyMock;

import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Transaction;

/**
 * Verifies jdo txn behavior across the following variables:
 * datasource type (txn | nontxn)
 * programmatic txn demarcation (yes | no)
 * operation (read | write)
 * support for that operation outside a txn (yes | no)
 * 
 * See https://spreadsheets.google.com/a/google.com/pub?key=p8C3zgqqUfpstFKZ4ns1bQg
 * for all the gory details.
 *
 * @author Erick Armbrust <earmbrust@google.com>
 * @author Max Ross <maxr@google.com>
 */
public class JDOTransactionTest extends TestCase {

  private static int handleCounter = 0;

  private LocalDatastoreTestHelper ldth;
  private DatastoreService mockDatastoreService = EasyMock.createMock(DatastoreService.class);
  private com.google.appengine.api.datastore.Transaction mockTxn = EasyMock.createMock(
      com.google.appengine.api.datastore.Transaction.class);
  private DatastoreServiceRecordingImpl recordingImpl;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ldth = new LocalDatastoreTestHelper();
    ldth.setUp();
    recordingImpl = new DatastoreServiceRecordingImpl(mockDatastoreService, ldth.ds, mockTxn);
    DatastoreServiceFactoryInternal.setDatastoreService(recordingImpl);
    handleCounter = 0;
  }

  @Override
  protected void tearDown() throws Exception {
    EasyMock.reset(mockDatastoreService, mockTxn);
    ldth.tearDown(true);
    ldth = null;
    DatastoreServiceFactoryInternal.setDatastoreService(null);
    recordingImpl = null;
    super.tearDown();
  }

  /**
   * A new PersistenceManagerFactory should be fetched on a per-test basis.  The
   * DatastoreService within the DatastorePersistenceHandler is obtained via the
   * DatastoreServiceFactory, so this ensures that the "injected" factory impl
   * is returned.
   */
  private PersistenceManagerFactory getPersistenceManagerFactory(String pmfName) {
    return JDOHelper.getPersistenceManagerFactory(pmfName);
  }

  private void testWritePermutationWithExpectedDatastoreTxn(
      PersistenceManager pm, boolean explicitDemarcation,
      boolean nonTransactionalWrite) {
    EasyMock.expect(mockDatastoreService.beginTransaction()).andReturn(mockTxn);
    EasyMock.expect(mockDatastoreService.put(
        EasyMock.isA(com.google.appengine.api.datastore.Transaction.class),
        EasyMock.isA(Entity.class))).andReturn(null);
    EasyMock.expect(mockTxn.getId()).andReturn(Integer.toString(handleCounter++)).anyTimes();
    mockTxn.commit();
    EasyMock.replay(mockDatastoreService, mockTxn);

    Flight f1 = new Flight();
    f1.setName("Harold");
    f1.setOrigin("BOS");
    f1.setDest("MIA");
    f1.setYou(1);
    f1.setMe(2);

    Transaction txn = pm.currentTransaction();
    txn.setNontransactionalWrite(nonTransactionalWrite);
    if (explicitDemarcation) {
      txn.begin();
    }
    try {
      pm.makePersistent(f1);
    } finally {
      if (explicitDemarcation) {
        txn.commit();
      }
    }
    EasyMock.verify(mockDatastoreService, mockTxn);
    EasyMock.reset(mockDatastoreService, mockTxn);
  }

  private void testUpdatePermutationWithExpectedDatastoreTxn(
      PersistenceManagerFactory pmf, boolean explicitDemarcation, boolean nonTransactionalWrite)
      throws EntityNotFoundException {
    Entity flightEntity = Flight.newFlightEntity("Harold", "BOS", "MIA", 1, 2);
    ldth.ds.put(flightEntity);
    EasyMock.expect(mockDatastoreService.beginTransaction()).andReturn(mockTxn);
    EasyMock.expect(mockDatastoreService.get(
        EasyMock.isA(com.google.appengine.api.datastore.Transaction.class),
        EasyMock.isA(Key.class))).andReturn(flightEntity);
    EasyMock.expect(mockDatastoreService.get(
        EasyMock.isA(Key.class))).andReturn(flightEntity);
    EasyMock.expect(mockDatastoreService.put(
        EasyMock.isA(com.google.appengine.api.datastore.Transaction.class),
        EasyMock.isA(Entity.class))).andReturn(null);
    EasyMock.expect(mockTxn.getId()).andReturn(Integer.toString(handleCounter++)).anyTimes();
    mockTxn.commit();
    EasyMock.replay(mockDatastoreService, mockTxn);

    PersistenceManager pm = pmf.getPersistenceManager();
    Transaction txn = pm.currentTransaction();
    txn.setNontransactionalWrite(nonTransactionalWrite);
    if (explicitDemarcation) {
      txn.begin();
    }
    try {
      Flight f1 = pm.getObjectById(Flight.class, KeyFactory.keyToString(flightEntity.getKey()));
      f1.setYou(88);
    } finally {
      if (explicitDemarcation) {
        txn.commit();
      }
      pm.close();
    }
    EasyMock.verify(mockDatastoreService, mockTxn);
    EasyMock.reset(mockDatastoreService, mockTxn);
  }

  private void testReadPermutationWithExpectedDatastoreTxn(
      PersistenceManager pm, boolean explicitDemarcation,
      boolean nonTransactionalRead) throws EntityNotFoundException {

    EasyMock.expect(mockDatastoreService.beginTransaction()).andReturn(mockTxn);
    EasyMock.expect(mockDatastoreService.get(
        EasyMock.isA(com.google.appengine.api.datastore.Transaction.class),
        EasyMock.isA(Key.class))).andReturn(null);
    EasyMock.expect(mockTxn.getId()).andReturn(Integer.toString(handleCounter++)).anyTimes();
    mockTxn.commit();
    EasyMock.replay(mockDatastoreService, mockTxn);

    Entity f1 = Flight.newFlightEntity("foo", "bar", "baz", 1, 2);
    ldth.ds.put(f1);

    Transaction txn = pm.currentTransaction();
    txn.setNontransactionalRead(nonTransactionalRead);
    if (explicitDemarcation) {
      txn.begin();
    }
    try {
      pm.getObjectById(Flight.class, KeyFactory.keyToString(f1.getKey()));
    } finally {
      if (explicitDemarcation) {
        txn.commit();
      }
    }

    EasyMock.verify(mockDatastoreService, mockTxn);
    EasyMock.reset(mockDatastoreService, mockTxn);
  }

  private void testWritePermutationWithoutExpectedDatastoreTxn(
      PersistenceManager pm, boolean explicitDemarcation, boolean nonTransactionalOpAllowed) {
    EasyMock.expect(mockDatastoreService.put(EasyMock.isA(Entity.class))).andReturn(null);
    EasyMock.replay(mockDatastoreService, mockTxn);

    Flight f1 = new Flight();
    f1.setName("Harold");
    f1.setOrigin("BOS");
    f1.setDest("MIA");
    f1.setYou(1);
    f1.setMe(2);

    Transaction txn = pm.currentTransaction();
    txn.setNontransactionalWrite(nonTransactionalOpAllowed);
    txn.setNontransactionalRead(nonTransactionalOpAllowed);
    if (explicitDemarcation) {
      txn.begin();
    }
    try {
      pm.makePersistent(f1);
    } finally {
      if (explicitDemarcation) {
        txn.commit();
      }
    }

    EasyMock.verify(mockDatastoreService, mockTxn);
    EasyMock.reset(mockDatastoreService, mockTxn);
  }

  private void testUpdatePermutationWithoutExpectedDatastoreTxn(
      PersistenceManagerFactory pmf, boolean explicitDemarcation, boolean nonTransactionalOp)
      throws EntityNotFoundException {
    Entity flightEntity = Flight.newFlightEntity("Harold", "BOS", "MIA", 1, 2);
    ldth.ds.put(flightEntity);
    EasyMock.expect(mockDatastoreService.get(
        EasyMock.isA(Key.class))).andReturn(flightEntity);
    EasyMock.expect(mockDatastoreService.get(
        EasyMock.isA(Key.class))).andReturn(flightEntity);
    EasyMock.expect(mockDatastoreService.put(
        EasyMock.isA(Entity.class))).andReturn(null);
    EasyMock.expect(mockTxn.getId()).andReturn(Integer.toString(handleCounter++)).anyTimes();
    EasyMock.replay(mockDatastoreService, mockTxn);

    PersistenceManager pm = pmf.getPersistenceManager();
    Transaction txn = pm.currentTransaction();
    txn.setNontransactionalWrite(nonTransactionalOp);
    txn.setNontransactionalRead(nonTransactionalOp);
    if (explicitDemarcation) {
      txn.begin();
    }
    try {
      Flight f1 = pm.getObjectById(Flight.class, KeyFactory.keyToString(flightEntity.getKey()));
      f1.setYou(88);
    } finally {
      if (explicitDemarcation) {
        txn.commit();
      }
      pm.close();
    }
    EasyMock.verify(mockDatastoreService, mockTxn);
    EasyMock.reset(mockDatastoreService, mockTxn);
  }

  private void testReadPermutationWithoutExpectedDatastoreTxn(
      PersistenceManager pm, boolean explicitDemarcation,
      boolean nonTransactionalRead) throws EntityNotFoundException {
    EasyMock.expect(mockDatastoreService.get(EasyMock.isA(Key.class))).andReturn(null);
    EasyMock.replay(mockDatastoreService, mockTxn);

    Entity f1 = Flight.newFlightEntity("foo", "bar", "baz", 1, 2);
    ldth.ds.put(f1);

    Transaction txn = pm.currentTransaction();
    txn.setNontransactionalRead(nonTransactionalRead);
    if (explicitDemarcation) {
      txn.begin();
    }
    try {
      pm.getObjectById(Flight.class, KeyFactory.keyToString(f1.getKey()));
    } finally {
      if (explicitDemarcation) {
        txn.commit();
      }
    }
    EasyMock.verify(mockDatastoreService, mockTxn);
    EasyMock.reset(mockDatastoreService, mockTxn);
  }

  private void testIllegalWritePermutation(
      PersistenceManager pm, boolean explicitDemarcation, boolean nonTransactionalWrite) {

    EasyMock.replay(mockDatastoreService, mockTxn);

    Flight f1 = new Flight();
    f1.setName("Harold");
    f1.setOrigin("BOS");
    f1.setDest("MIA");
    f1.setYou(1);
    f1.setMe(2);

    Transaction txn = pm.currentTransaction();
    txn.setNontransactionalWrite(nonTransactionalWrite);
    if (explicitDemarcation) {
      txn.begin();
    }
    try {
      pm.makePersistent(f1);
      fail("Expected exception");
    } catch (TransactionNotWritableException e) {
      // good
    }
    EasyMock.verify(mockDatastoreService, mockTxn);
    EasyMock.reset(mockDatastoreService, mockTxn);
  }

  private void testIllegalUpdatePermutation(
      PersistenceManagerFactory pmf, boolean explicitDemarcation, boolean nonTransactionalOp)
      throws EntityNotFoundException {
    Entity flightEntity = Flight.newFlightEntity("Harold", "BOS", "MIA", 1, 2);
    ldth.ds.put(flightEntity);
    EasyMock.replay(mockDatastoreService, mockTxn);

    PersistenceManager pm = pmf.getPersistenceManager();
    Transaction txn = pm.currentTransaction();
    txn.setNontransactionalWrite(nonTransactionalOp);
    txn.setNontransactionalRead(nonTransactionalOp);
    if (explicitDemarcation) {
      txn.begin();
    }
    try {
      Flight f = pm.getObjectById(Flight.class, KeyFactory.keyToString(flightEntity.getKey()));
      fail("expected exception");
    } catch (TransactionNotReadableException tnre) {
      // good
    } finally {
      if (explicitDemarcation) {
        txn.commit();
      }
      pm.close();
    }
    EasyMock.verify(mockDatastoreService, mockTxn);
    EasyMock.reset(mockDatastoreService, mockTxn);

  }

  private void testIllegalReadPermutation(
      PersistenceManager pm, boolean explicitDemarcation,
      boolean nonTransactionalRead) throws EntityNotFoundException {

    EasyMock.replay(mockDatastoreService, mockTxn);

    Entity f1 = Flight.newFlightEntity("foo", "bar", "baz", 1, 2);
    ldth.ds.put(f1);

    Transaction txn = pm.currentTransaction();
    txn.setNontransactionalRead(nonTransactionalRead);
    if (explicitDemarcation) {
      txn.begin();
    }
    try {
      pm.getObjectById(Flight.class, KeyFactory.keyToString(f1.getKey()));
      fail("Expected exception");
    } catch (TransactionNotReadableException e) {
      // good
    }
    EasyMock.verify(mockDatastoreService, mockTxn);
    EasyMock.reset(mockDatastoreService, mockTxn);
  }

  private static final boolean EXPLICIT_DEMARCATION = true;
  private static final boolean NO_EXPLICIT_DEMARCATION = false;
  private static final boolean NON_TXN_OP_ALLOWED = true;
  private static final boolean NON_TXN_OP_NOT_ALLOWED = false;

  public void testWritesWithDatastoreTxn() throws Exception {
    PersistenceManagerFactory pmf = getPersistenceManagerFactory(
        JDOTestCase.PersistenceManagerFactoryName.transactional.name());
    PersistenceManager pm = pmf.getPersistenceManager();
    testWritePermutationWithExpectedDatastoreTxn(pm, EXPLICIT_DEMARCATION, NON_TXN_OP_ALLOWED);
    testWritePermutationWithExpectedDatastoreTxn(pm, EXPLICIT_DEMARCATION, NON_TXN_OP_NOT_ALLOWED);
    pm.close();
    pmf.close();
  }

  public void testUpdatesWithDatastoreTxn() throws Exception {
    PersistenceManagerFactory pmf = getPersistenceManagerFactory(
        JDOTestCase.PersistenceManagerFactoryName.transactional.name());
    testUpdatePermutationWithExpectedDatastoreTxn(pmf, EXPLICIT_DEMARCATION, NON_TXN_OP_ALLOWED);
    testUpdatePermutationWithExpectedDatastoreTxn(pmf, EXPLICIT_DEMARCATION, NON_TXN_OP_NOT_ALLOWED);
    pmf.close();
  }

  public void testReadsWithDatastoreTxn() throws Exception {
    PersistenceManagerFactory pmf = getPersistenceManagerFactory(
        JDOTestCase.PersistenceManagerFactoryName.transactional.name());
    PersistenceManager pm = pmf.getPersistenceManager();
    testReadPermutationWithExpectedDatastoreTxn(pm, EXPLICIT_DEMARCATION, NON_TXN_OP_ALLOWED);
    testReadPermutationWithExpectedDatastoreTxn(pm, EXPLICIT_DEMARCATION, NON_TXN_OP_NOT_ALLOWED);
    pm.close();
    pmf.close();
  }

  public void testWritesWithoutDatastoreTxn() throws Exception {
    PersistenceManagerFactory pmf = getPersistenceManagerFactory(
        JDOTestCase.PersistenceManagerFactoryName.transactional.name());
    PersistenceManager pm = pmf.getPersistenceManager();
    testWritePermutationWithoutExpectedDatastoreTxn(pm, NO_EXPLICIT_DEMARCATION, NON_TXN_OP_ALLOWED);
    pm.close();
    pmf.close();

    pmf = getPersistenceManagerFactory(
        JDOTestCase.PersistenceManagerFactoryName.nontransactional.name());
    pm = pmf.getPersistenceManager();
    testWritePermutationWithoutExpectedDatastoreTxn(pm, EXPLICIT_DEMARCATION, NON_TXN_OP_ALLOWED);
    testWritePermutationWithoutExpectedDatastoreTxn(pm, EXPLICIT_DEMARCATION, NON_TXN_OP_NOT_ALLOWED);
    testWritePermutationWithoutExpectedDatastoreTxn(pm, NO_EXPLICIT_DEMARCATION, NON_TXN_OP_ALLOWED);

    pm.close();
    pmf.close();
  }

  public void testUpdatesWithoutDatastoreTxn() throws Exception {
    PersistenceManagerFactory pmf = getPersistenceManagerFactory(
        JDOTestCase.PersistenceManagerFactoryName.transactional.name());
    testUpdatePermutationWithoutExpectedDatastoreTxn(pmf, NO_EXPLICIT_DEMARCATION, NON_TXN_OP_ALLOWED);
    pmf.close();

    pmf = getPersistenceManagerFactory(
        JDOTestCase.PersistenceManagerFactoryName.nontransactional.name());
    testUpdatePermutationWithoutExpectedDatastoreTxn(pmf, EXPLICIT_DEMARCATION, NON_TXN_OP_ALLOWED);
    testUpdatePermutationWithoutExpectedDatastoreTxn(pmf, EXPLICIT_DEMARCATION, NON_TXN_OP_NOT_ALLOWED);
    testUpdatePermutationWithoutExpectedDatastoreTxn(pmf, NO_EXPLICIT_DEMARCATION, NON_TXN_OP_ALLOWED);

    pmf.close();
  }

  public void testReadsWithoutDatastoreTxn() throws Exception {
    PersistenceManagerFactory pmf = getPersistenceManagerFactory(
        JDOTestCase.PersistenceManagerFactoryName.transactional.name());
    PersistenceManager pm = pmf.getPersistenceManager();
    testReadPermutationWithoutExpectedDatastoreTxn(pm, NO_EXPLICIT_DEMARCATION, NON_TXN_OP_ALLOWED);
    pm.close();
    pmf.close();

    pmf = getPersistenceManagerFactory(
        JDOTestCase.PersistenceManagerFactoryName.nontransactional.name());
    pm = pmf.getPersistenceManager();
    testReadPermutationWithoutExpectedDatastoreTxn(pm, EXPLICIT_DEMARCATION, NON_TXN_OP_ALLOWED);
    testReadPermutationWithoutExpectedDatastoreTxn(pm, EXPLICIT_DEMARCATION, NON_TXN_OP_NOT_ALLOWED);
    testReadPermutationWithoutExpectedDatastoreTxn(pm, NO_EXPLICIT_DEMARCATION, NON_TXN_OP_ALLOWED);

    pm.close();
    pmf.close();
  }

  public void testIllegalWrites() throws Exception {
    PersistenceManagerFactory pmf = getPersistenceManagerFactory(
        JDOTestCase.PersistenceManagerFactoryName.transactional.name());
    PersistenceManager pm = pmf.getPersistenceManager();
    testIllegalWritePermutation(pm, NO_EXPLICIT_DEMARCATION, NON_TXN_OP_NOT_ALLOWED);
    pm.close();
    pmf.close();
    pmf = getPersistenceManagerFactory(
        JDOTestCase.PersistenceManagerFactoryName.nontransactional.name());
    pm = pmf.getPersistenceManager();
    testIllegalWritePermutation(pm, NO_EXPLICIT_DEMARCATION, NON_TXN_OP_NOT_ALLOWED);
    pm.close();
    pmf.close();
  }

  public void testIllegalUpdates() throws Exception {
    PersistenceManagerFactory pmf = getPersistenceManagerFactory(
        JDOTestCase.PersistenceManagerFactoryName.transactional.name());
    testIllegalUpdatePermutation(pmf, NO_EXPLICIT_DEMARCATION, NON_TXN_OP_NOT_ALLOWED);
    pmf.close();
    pmf = getPersistenceManagerFactory(
        JDOTestCase.PersistenceManagerFactoryName.nontransactional.name());
    testIllegalUpdatePermutation(pmf, NO_EXPLICIT_DEMARCATION, NON_TXN_OP_NOT_ALLOWED);
    pmf.close();
  }

  public void testIllegalReads() throws Exception {
    PersistenceManagerFactory pmf = getPersistenceManagerFactory(
        JDOTestCase.PersistenceManagerFactoryName.transactional.name());
    PersistenceManager pm = pmf.getPersistenceManager();
    testIllegalReadPermutation(pm, NO_EXPLICIT_DEMARCATION, NON_TXN_OP_NOT_ALLOWED);
    pm.close();
    pmf.close();
    pmf = getPersistenceManagerFactory(
        JDOTestCase.PersistenceManagerFactoryName.nontransactional.name());
    pm = pmf.getPersistenceManager();
    testIllegalReadPermutation(pm, NO_EXPLICIT_DEMARCATION, NON_TXN_OP_NOT_ALLOWED);
    pm.close();
    pmf.close();
  }

}