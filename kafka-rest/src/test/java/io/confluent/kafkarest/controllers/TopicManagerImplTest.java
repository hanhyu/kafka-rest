/*
 * Copyright 2020 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.kafkarest.controllers;

import static io.confluent.kafkarest.TestUtils.failedFuture;
import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import io.confluent.kafkarest.entities.Broker;
import io.confluent.kafkarest.entities.Cluster;
import io.confluent.kafkarest.entities.Partition;
import io.confluent.kafkarest.entities.PartitionReplica;
import io.confluent.kafkarest.entities.Topic;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.NotFoundException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.errors.TimeoutException;
import org.easymock.EasyMockRule;
import org.easymock.Mock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class TopicManagerImplTest {

  private static final String CLUSTER_ID = "cluster-1";

  private static final Node NODE_1 = new Node(1, "broker-1", 9091);
  private static final Node NODE_2 = new Node(2, "broker-2", 9092);
  private static final Node NODE_3 = new Node(3, "broker-3", 9093);

  private static final Broker BROKER_1 = Broker.fromNode(CLUSTER_ID, NODE_1);
  private static final Broker BROKER_2 = Broker.fromNode(CLUSTER_ID, NODE_2);
  private static final Broker BROKER_3 = Broker.fromNode(CLUSTER_ID, NODE_3);

  private static final Cluster CLUSTER =
      new Cluster(CLUSTER_ID, BROKER_1, Arrays.asList(BROKER_1, BROKER_2, BROKER_3));

  private static final List<TopicListing> TOPIC_LISTINGS =
      Arrays.asList(
          new TopicListing("topic-1", true),
          new TopicListing("topic-2", true),
          new TopicListing("topic-3", false));

  private static final TopicDescription TOPIC_DESCRIPTION_1 =
      new TopicDescription(
          "topic-1",
          /* internal= */ true,
          Arrays.asList(
              new TopicPartitionInfo(
                  0, NODE_1, Arrays.asList(NODE_1, NODE_2, NODE_3), singletonList(NODE_1)),
              new TopicPartitionInfo(
                  1, NODE_2, Arrays.asList(NODE_1, NODE_2, NODE_3), singletonList(NODE_2)),
              new TopicPartitionInfo(
                  2, NODE_3, Arrays.asList(NODE_1, NODE_2, NODE_3), singletonList(NODE_3))),
          /* authorizedOperations= */ new HashSet<>());

  private static final TopicDescription TOPIC_DESCRIPTION_2 =
      new TopicDescription(
          "topic-2",
          /* internal= */ true,
          Arrays.asList(
              new TopicPartitionInfo(
                  0, NODE_3, Arrays.asList(NODE_1, NODE_2, NODE_3), singletonList(NODE_3)),
              new TopicPartitionInfo(
                  1, NODE_1, Arrays.asList(NODE_1, NODE_2, NODE_3), singletonList(NODE_1)),
              new TopicPartitionInfo(
                  2, NODE_2, Arrays.asList(NODE_1, NODE_2, NODE_3), singletonList(NODE_2))),
          /* authorizedOperations= */ new HashSet<>());

  private static final TopicDescription TOPIC_DESCRIPTION_3 =
      new TopicDescription(
          "topic-3",
          /* internal= */ false,
          Arrays.asList(
              new TopicPartitionInfo(
                  0, NODE_2, Arrays.asList(NODE_1, NODE_2, NODE_3), singletonList(NODE_2)),
              new TopicPartitionInfo(
                  1, NODE_3, Arrays.asList(NODE_1, NODE_2, NODE_3), singletonList(NODE_3)),
              new TopicPartitionInfo(
                  2, NODE_1, Arrays.asList(NODE_1, NODE_2, NODE_3), singletonList(NODE_1))),
          /* authorizedOperations= */ new HashSet<>());

  private static final Topic TOPIC_1 =
      new Topic(
          CLUSTER_ID,
          "topic-1",
          new Properties(),
          Arrays.asList(
              new Partition(
                  CLUSTER_ID,
                  "topic-1",
                  /* partitionId= */ 0,
                  Arrays.asList(
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-1",
                          /* partitionId= */ 0,
                          /* brokerId= */ 1,
                          /* isLeader= */ true,
                          /* isInSync= */ true),
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-1",
                          /* partitionId= */ 0,
                          /* brokerId= */ 2,
                          /* isLeader= */ false,
                          /* isInSync= */ false),
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-1",
                          /* partitionId= */ 0,
                          /* brokerId= */ 3,
                          /* isLeader= */ false,
                          /* isInSync= */ false))),
              new Partition(
                  CLUSTER_ID,
                  "topic-1",
                  /* partitionId= */ 1,
                  Arrays.asList(
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-1",
                          /* partitionId= */ 1,
                          /* brokerId= */ 1,
                          /* isLeader= */ false,
                          /* isInSync= */ false),
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-1",
                          /* partitionId= */ 1,
                          /* brokerId= */ 2,
                          /* isLeader= */ true,
                          /* isInSync= */ true),
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-1",
                          /* partitionId= */ 1,
                          /* brokerId= */ 3,
                          /* isLeader= */ false,
                          /* isInSync= */ false))),
              new Partition(
                  CLUSTER_ID,
                  "topic-1",
                  /* partitionId= */2,
                  Arrays.asList(
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-1",
                          /* partitionId= */ 2,
                          /* brokerId= */ 1,
                          /* isLeader= */ false,
                          /* isInSync= */ false),
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-1",
                          /* partitionId= */ 2,
                          /* brokerId= */ 2,
                          /* isLeader= */ false,
                          /* isInSync= */ false),
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-1",
                          /* partitionId= */ 2,
                          /* brokerId= */ 3,
                          /* isLeader= */ true,
                          /* isInSync= */ true)))),
          /* replicationFactor= */ 3,
          /* isInternal= */ true);

  private static final Topic TOPIC_2 =
      new Topic(
          CLUSTER_ID,
          "topic-2",
          new Properties(),
          Arrays.asList(
              new Partition(
                  CLUSTER_ID,
                  "topic-2",
                  /* partitionId= */ 0,
                  Arrays.asList(
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-2",
                          /* partitionId= */ 0,
                          /* brokerId= */ 1,
                          /* isLeader= */ false,
                          /* isInSync= */ false),
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-2",
                          /* partitionId= */ 0,
                          /* brokerId= */ 2,
                          /* isLeader= */ false,
                          /* isInSync= */ false),
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-2",
                          /* partitionId= */ 0,
                          /* brokerId= */ 3,
                          /* isLeader= */ true,
                          /* isInSync= */ true))),
              new Partition(
                  CLUSTER_ID,
                  "topic-2",
                  /* partitionId= */ 1,
                  Arrays.asList(
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-2",
                          /* partitionId= */ 1,
                          /* brokerId= */ 1,
                          /* isLeader= */ true,
                          /* isInSync= */ true),
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-2",
                          /* partitionId= */ 1,
                          /* brokerId= */ 2,
                          /* isLeader= */ false,
                          /* isInSync= */ false),
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-2",
                          /* partitionId= */ 1,
                          /* brokerId= */ 3,
                          /* isLeader= */ false,
                          /* isInSync= */ false))),
              new Partition(
                  CLUSTER_ID,
                  "topic-2",
                  /* partitionId= */2,
                  Arrays.asList(
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-2",
                          /* partitionId= */ 2,
                          /* brokerId= */ 1,
                          /* isLeader= */ false,
                          /* isInSync= */ false),
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-2",
                          /* partitionId= */ 2,
                          /* brokerId= */ 2,
                          /* isLeader= */ true,
                          /* isInSync= */ true),
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-2",
                          /* partitionId= */ 2,
                          /* brokerId= */ 3,
                          /* isLeader= */ false,
                          /* isInSync= */ false)))),
          /* replicationFactor= */ 3,
          /* isInternal= */ true);

  private static final Topic TOPIC_3 =
      new Topic(
          CLUSTER_ID,
          "topic-3",
          new Properties(),
          Arrays.asList(
              new Partition(
                  CLUSTER_ID,
                  "topic-3",
                  /* partitionId= */ 0,
                  Arrays.asList(
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-3",
                          /* partitionId= */ 0,
                          /* brokerId= */ 1,
                          /* isLeader= */ false,
                          /* isInSync= */ false),
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-3",
                          /* partitionId= */ 0,
                          /* brokerId= */ 2,
                          /* isLeader= */ true,
                          /* isInSync= */ true),
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-3",
                          /* partitionId= */ 0,
                          /* brokerId= */ 3,
                          /* isLeader= */ false,
                          /* isInSync= */ false))),
              new Partition(
                  CLUSTER_ID,
                  "topic-3",
                  /* partitionId= */ 1,
                  Arrays.asList(
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-3",
                          /* partitionId= */ 1,
                          /* brokerId= */ 1,
                          /* isLeader= */ false,
                          /* isInSync= */ false),
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-3",
                          /* partitionId= */ 1,
                          /* brokerId= */ 2,
                          /* isLeader= */ false,
                          /* isInSync= */ false),
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-3",
                          /* partitionId= */ 1,
                          /* brokerId= */ 3,
                          /* isLeader= */ true,
                          /* isInSync= */ true))),
              new Partition(
                  CLUSTER_ID,
                  "topic-3",
                  /* partitionId= */2,
                  Arrays.asList(
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-3",
                          /* partitionId= */ 2,
                          /* brokerId= */ 1,
                          /* isLeader= */ true,
                          /* isInSync= */ true),
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-3",
                          /* partitionId= */ 2,
                          /* brokerId= */ 2,
                          /* isLeader= */ false,
                          /* isInSync= */ false),
                      new PartitionReplica(
                          CLUSTER_ID,
                          "topic-3",
                          /* partitionId= */ 2,
                          /* brokerId= */ 3,
                          /* isLeader= */ false,
                          /* isInSync= */ false)))),
          /* replicationFactor= */ 3,
          /* isInternal= */ false);

  @Rule
  public final EasyMockRule mocks = new EasyMockRule(this);

  @Mock
  private Admin adminClient;

  @Mock
  private ClusterManager clusterManager;

  @Mock
  private ListTopicsResult listTopicsResult;

  @Mock
  private DescribeTopicsResult describeTopicResult;

  private TopicManagerImpl topicManager;

  @Before
  public void setUp() {
    topicManager = new TopicManagerImpl(adminClient, clusterManager);
  }

  @Test
  public void listTopics_existingCluster_returnsTopics() throws Exception {
    expect(clusterManager.getCluster(CLUSTER_ID)).andReturn(completedFuture(Optional.of(CLUSTER)));
    expect(adminClient.listTopics()).andReturn(listTopicsResult);
    expect(listTopicsResult.listings()).andReturn(KafkaFuture.completedFuture(TOPIC_LISTINGS));
    expect(adminClient.describeTopics(anyObject())).andReturn(describeTopicResult);
    expect(describeTopicResult.all())
        .andReturn(
            KafkaFuture.completedFuture(
                createTopicDescriptionMap(
                    TOPIC_DESCRIPTION_1, TOPIC_DESCRIPTION_2, TOPIC_DESCRIPTION_3)));
    replay(clusterManager, adminClient, listTopicsResult, describeTopicResult);

    List<Topic> topics = topicManager.listTopics(CLUSTER_ID).get();

    assertEquals(Arrays.asList(TOPIC_1, TOPIC_2, TOPIC_3), topics);
  }

  @Test
  public void listTopics_timeoutException_throwsTimeoutException() throws Exception {
    expect(clusterManager.getCluster(CLUSTER_ID)).andReturn(completedFuture(Optional.of(CLUSTER)));
    expect(adminClient.listTopics()).andReturn(listTopicsResult);
    expect(listTopicsResult.listings()).andReturn(failedFuture(new TimeoutException()));
    replay(clusterManager, adminClient, listTopicsResult);

    try {
      topicManager.listTopics(CLUSTER_ID).get();
      fail();
    } catch (ExecutionException e) {
      assertEquals(TimeoutException.class, e.getCause().getClass());
    }
  }

  @Test
  public void listTopic_nonExistingCluster_throwsNotFoundException() throws Exception {
    expect(clusterManager.getCluster(CLUSTER_ID)).andReturn(completedFuture(Optional.empty()));
    replay(clusterManager);

    try {
      topicManager.listTopics(CLUSTER_ID).get();
      fail();
    } catch (ExecutionException e) {
      assertEquals(NotFoundException.class, e.getCause().getClass());
    }
  }

  @Test
  public void getTopic_existingTopic_returnsTopic() throws Exception {
    expect(clusterManager.getCluster(CLUSTER_ID)).andReturn(completedFuture(Optional.of(CLUSTER)));
    expect(adminClient.describeTopics(anyObject())).andReturn(describeTopicResult);
    expect(describeTopicResult.all())
        .andReturn(KafkaFuture.completedFuture(createTopicDescriptionMap(TOPIC_DESCRIPTION_1)));
    replay(clusterManager, adminClient, describeTopicResult);

    Topic topic = topicManager.getTopic(CLUSTER_ID, TOPIC_1.getName()).get().get();

    assertEquals(TOPIC_1, topic);
  }

  @Test
  public void getTopic_nonExistingCluster_throwsNotFoundException() throws Exception {
    expect(clusterManager.getCluster(CLUSTER_ID)).andReturn(completedFuture(Optional.empty()));
    expect(adminClient.describeTopics(anyObject())).andReturn(describeTopicResult);
    replay(clusterManager);

    try {
      topicManager.listTopics(CLUSTER_ID).get();
      fail();
    } catch (ExecutionException e) {
      assertEquals(NotFoundException.class, e.getCause().getClass());
    }
  }

  @Test
  public void getTopic_nonExistingTopic_returnsEmpty() throws Exception {
    expect(clusterManager.getCluster(CLUSTER_ID)).andReturn(completedFuture(Optional.of(CLUSTER)));
    expect(adminClient.describeTopics(anyObject())).andReturn(describeTopicResult);
    expect(describeTopicResult.all()).andReturn(KafkaFuture.completedFuture(new HashMap<>()));
    replay(clusterManager, adminClient, describeTopicResult);

    Optional<Topic> topic = topicManager.getTopic(CLUSTER_ID, TOPIC_1.getName()).get();

    assertFalse(topic.isPresent());
  }

  private static Map<String, TopicDescription> createTopicDescriptionMap(
      TopicDescription... topicDescriptions) {
    HashMap<String, TopicDescription> topicDescriptionMap = new HashMap<>();
    for (TopicDescription topicDescription : topicDescriptions) {
      topicDescriptionMap.put(topicDescription.name(), topicDescription);
    }
    return topicDescriptionMap;
  }
}
