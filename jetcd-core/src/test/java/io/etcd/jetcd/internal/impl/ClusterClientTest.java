/**
 * Copyright 2017 The jetcd authors
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
package io.etcd.jetcd.internal.impl;

import io.etcd.jetcd.Client;
import io.etcd.jetcd.Cluster;
import io.etcd.jetcd.cluster.Member;
import io.etcd.jetcd.cluster.MemberAddResponse;
import io.etcd.jetcd.cluster.MemberListResponse;
import io.etcd.jetcd.launcher.EtcdCluster;
import io.etcd.jetcd.launcher.EtcdClusterFactory;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import org.testng.asserts.Assertion;

/**
 * test etcd cluster client
 */
public class ClusterClientTest {
  private static final EtcdCluster CLUSTER = EtcdClusterFactory.buildCluster("cluster-client", 3 ,false);

  private final Assertion assertion = new Assertion();
  private Member addedMember;

  private List<URI> endpoints;
  private List<URI> peerUrls;

  /**
   * test list cluster function
   */

  @BeforeTest
  public void setUp() throws InterruptedException {
    CLUSTER.start();

    endpoints = CLUSTER.getClientEndpoints();
    peerUrls = CLUSTER.getPeerEndpoints();
    TimeUnit.SECONDS.sleep(5);
  }

  @Test
  public void testListCluster()
      throws ExecutionException, InterruptedException {
    Client client = Client.builder().endpoints(endpoints).build();
    Cluster clusterClient = client.getClusterClient();
    MemberListResponse response = clusterClient.listMember().get();
    assertion
        .assertEquals(response.getMembers().size(), 3, "Members: " + response.getMembers().size());
  }

  /**
   * test add cluster function, added member will be removed by testDeleteMember
   */
  @Test(dependsOnMethods = "testListCluster")
  public void testAddMember()
      throws ExecutionException, InterruptedException, TimeoutException {
    Client client = Client.builder()
        .endpoints(endpoints.subList(0, 2))
        .build();

    Cluster clusterClient = client.getClusterClient();
    MemberListResponse response = clusterClient.listMember().get();
    assertion.assertEquals(response.getMembers().size(), 3);
    CompletableFuture<MemberAddResponse> responseListenableFuture = clusterClient.addMember(peerUrls.subList(2, 3));
    MemberAddResponse addResponse = responseListenableFuture.get(5, TimeUnit.SECONDS);
    addedMember = addResponse.getMember();
    assertion.assertNotNull(addedMember, "added member: " + addedMember.getId());
  }

  /**
   * test update peer url for member
   */
  @Test(dependsOnMethods = "testAddMember")
  public void testUpdateMember() {

    Throwable throwable = null;
    try {
      Client client = Client.builder()
          .endpoints(endpoints.subList(1, 3))
          .build();

      Cluster clusterClient = client.getClusterClient();
      MemberListResponse response = clusterClient.listMember().get();
      List<URI> newPeerUrl = peerUrls.subList(0, 1);
      clusterClient.updateMember(response.getMembers().get(0).getId(), newPeerUrl)
          .get();
    } catch (Exception e) {
      System.out.println(e);
      throwable = e;
    }
    assertion.assertNull(throwable, "update for member");
  }

  /**
   * test remove member from cluster, the member is added by testAddMember
   */
  @Test(dependsOnMethods = "testUpdateMember")
  public void testDeleteMember()
      throws ExecutionException, InterruptedException {
    Client client = Client.builder()
        .endpoints(endpoints.subList(0, 2))
        .build();

    Cluster clusterClient = client.getClusterClient();
    clusterClient.removeMember(addedMember.getId()).get();
    int newCount = clusterClient.listMember().get().getMembers().size();
    assertion.assertEquals(newCount, 3,
        "delete added member(" + addedMember.getId() + "), and left " + newCount + " members");
  }

  @AfterTest
  public void tearDown() {
    CLUSTER.close();
  }
}
