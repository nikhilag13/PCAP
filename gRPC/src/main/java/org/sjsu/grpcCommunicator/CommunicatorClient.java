/*
 * Copyright 2015, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *
 *    * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sjsu.grpcCommunicator;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;
//import java.util.logging.Level;
//import java.util.logging.Logger;
import com.mongodb.*;
import org.apache.log4j.Logger;
import java.util.*;
import java.lang.*;

/**
 * A simple client that requests a greeting from the {@link CommunicatorServer}.
 */
public class CommunicatorClient {
  private static final Logger logger = Logger.getLogger(CommunicatorClient.class.getName());

  private ManagedChannel channel;
  private CommunicatorGrpc.CommunicatorBlockingStub blockingStub;

  MongoClient mongoClient = new MongoClient("localhost", 27017);
  DB database = mongoClient.getDB("cmpe295Project");

  /** Construct client connecting to HelloWorld server at {@code host:port}. */
  public CommunicatorClient(String host, int port) {
    channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    blockingStub = CommunicatorGrpc.newBlockingStub(channel);
  }

  public void shutdown() throws InterruptedException {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  /** Say hello to server. */
  public void greet(String name) {
    try {
      logger.info("Will try to greet " + name + " ...");
      HelloRequest request = HelloRequest.newBuilder().setName(name).build();
      HelloResponse response = blockingStub.sayHello(request);
      logger.info("Greeting: " + response.getMessage());
    } catch (RuntimeException e) {
      //logger.log(Level.WARNING, "RPC failed", e);
      logger.error("RPC failed", e);
      return;
    }
  }

  /** Say hello to server. */
//  public void getNodeSize(int size, String id) {
//    try {
//      logger.info("size is : " + size + " node is : "+ id);
//      MySize request = MySize.newBuilder().setSize(size).build();
//      AccomodateChild response = blockingStub.size(request);
//      logger.info("Greeting: " + response.getMessage());
//    } catch (RuntimeException e) {
//      logger.log(Level.WARNING, "RPC failed", e);
//      return;
//    }
//  }

  /** Start stage one clustering. **/
  public void startStageOneCluster(Node node, String ipAddress) {
    String[] strArr = ipAddress.split(":");
    String host = strArr[0];
    int port = Integer.valueOf(strArr[1]);
    channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext(true)
            .build();
    blockingStub = CommunicatorGrpc.newBlockingStub(channel);
    //CommunicatorClient(nid, port);//port num?
    try {
      sendSize(node, blockingStub);

    } catch (RuntimeException e) {
      logger.error("Node:{} - {}".format(node.getId(), e));
      logger.error(e);

    } finally {

      channel.shutdown();
//      blockingStub = "None";
//      channel = "None";
      Runtime.getRuntime().gc();
    }
  }


  public void sendSize(Node node, CommunicatorGrpc.CommunicatorBlockingStub blockingStub ) {

    System.out.println(node.getSize());
    logger.info("Node: %s - Starting function sendSize" +(node.getId()));
    MySize request = MySize.newBuilder().setSize(node.getSize()).build();
    AccomodateChild response = blockingStub.size(request);
    String sizeRPC = response.getMessage();
    logger.info("Node:" + node.getId() + " - Successfully sent the size message of size" + node.getSize() + " to parentId:" + node.getParent_Id());
    logger.info("Node:" + node.getParent_Id() + "- Responded to Size RPC with reply:" + sizeRPC);

    DBCollection collection = database.getCollection("spanningtree");

    BasicDBObject query = new BasicDBObject();
    query.put("nodeId", node.getId());

    if (sizeRPC == "Prune") {
      logger.info("Node:" + node.getId() + " - Got Prune");
      // Become a clusterhead and send Cluster RPC to children
      node.setCluster_head_Id(node.getId());
      node.setParent_Id("None");
      // Set I am the cluster
      node.setIs_Cluster_head(1);
      node.setState("free");
      try {

        BasicDBObject newDocument = new BasicDBObject();
        newDocument.put("'isClusterhead'", node.getIs_Cluster_head());
        newDocument.put("'parentId'", node.getParent_Id());
        newDocument.put("'clusterheadId'", node.getCluster_head_Id());
        newDocument.put("'hopcount'", node.getHop_count());
        newDocument.put("'size'", node.getSize());
        newDocument.put("'state'", node.getState());

        BasicDBObject updateObject = new BasicDBObject();
        updateObject.put("$set", newDocument);

        collection.update(query, updateObject);

      } catch (RuntimeException e) {
        logger.error("Node:" + node.getId() + "- not able to update db");
        logger.error(e);
      }
      //sendCluster(node);
    }
    else {
      logger.info("Node:" + node.getId() + "Didn't get Prune");
      //Do nothing if the child is accepted into the current cluster
      //Might need to add cluster ID to the central lookup #Later
    }
  }



  /**
   * Greet server. If provided, the first element of {@code args} is the name to use in the
   * greeting.
   */
  public static void main(String[] args) throws Exception {
    CommunicatorClient client = new CommunicatorClient("localhost", 50051);
    try {
      /* Access a service running on the local machine on port 50051 */
      String user = "world";
      if (args.length > 0) {
        user = args[0]; /* Use the arg as the name to greet if provided */
      }
      client.greet(user);
//      client.getNodeSize(3, "1");
    } finally {
      client.shutdown();
    }
  }
}