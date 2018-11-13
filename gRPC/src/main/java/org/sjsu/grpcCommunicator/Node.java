package org.sjsu.grpcCommunicator;

import com.mongodb.*;
import org.apache.http.util.ExceptionUtils;
import org.apache.log4j.Logger;
import java.util.*;


public class Node {

    final static Logger logger = Logger.getLogger(Node.class);

    String id;
    String parent_Id;
    String ip_address;
    List<String> child_list_Id;
    int dist;


    String cluster_head_Id;
    int hop_count;
    String rack_location;
    List<String> sub_tree_list;
    List<String> neighbour_list;
    int weight;
    int size;
    int child_request_counter;
    int initial_node_child_length ;
    String best_node_id;
    int best_node_hop_count;
    String best_node_cluster_head_Id;
    List<String>  neighbor_ID;
    public HashSet<String> neighbour_Hello_Array; //Phase 2



    String shift_Node_Id ;
    int shift_Node_Sum ;
    int shift_Node_Cluster ;

    int is_Cluster_head ;
    String state;
    {
        System.out.println("Node Object Created ");
    }

    NodeIdsList nodeIdsList =new NodeIdsList();

    MongoClient mongo = new MongoClient("localhost", 27017);
    DB db = mongo.getDB("cmpe295Project");

    // get a single collection
    DBCollection collection = db.getCollection("spanningtree");
    WeightMatrix weigthMatrix = new WeightMatrix();
    CommunicatorClient client = new CommunicatorClient();

    public Node(){

    }

    public Node(int node_id){
        this.id = String.valueOf(node_id);

        try {

            System.out.println("Constructor "+ this.id);
            logger.info("Constructor "+ this.id);
            BasicDBObject query = new BasicDBObject();
            query.put("node_id", this.id);

            DBObject document = collection.findOne(query);
            logger.info("Constructor 1"+ this.id);

            ip_address =nodeIdsList.getNodeIdsList().get(node_id);
            parent_Id = (String) document.get("parent_Id");

            logger.info("Constructor 2 parent"+ parent_Id);
            child_list_Id = new ArrayList<String>();
            BasicDBList list = (BasicDBList)document.get("child_list_Id");
            if(list!=null) {
                for (Object el : list) {
                    child_list_Id.add((String) el);
                }
            }
            if((document.get("dist")!=null)){
                dist =(int) document.get("dist");
            }

            if((document.get("cluster_head_Id")!=null)){
                cluster_head_Id =(String) document.get("cluster_head_Id");
            }
            hop_count=0;

            rack_location= (String)document.get("rack_location");

            sub_tree_list = new ArrayList<String>();
            BasicDBList list1 = (BasicDBList)document.get("sub_tree_list");
            if(list1!=null) {
                for (Object el : list1) {
                    sub_tree_list.add((String) el);
                }
            }

            neighbour_list = new ArrayList<String>();
            BasicDBList list2 = (BasicDBList)document.get("sub_tree_list");
            if(list2!=null) {
                for (Object el : list2) {
                    neighbour_list.add((String) el);
                }
            }

            weight = weigthMatrix.getWeight(id);
            size= weight;
            child_request_counter=0;
            initial_node_child_length=0;
            best_node_id=id;
            best_node_hop_count = hop_count;
            best_node_cluster_head_Id=cluster_head_Id;

            neighbor_ID = new ArrayList<String>();
            logger.info("constructor get_Neighbors "+ this.id);
            get_Neighbors();

            neighbour_Hello_Array = new HashSet<>(); //Phase 2

            initial_node_child_length= child_list_Id.size();
            shift_Node_Sum=0;
            shift_Node_Cluster=0;
            shift_Node_Id=null;

            if((document.get("is_Cluster_head")!=null)){
                is_Cluster_head =(int) document.get("is_Cluster_head");
            }

            state=(String) document.get("state");
            logger.info(" Starting Phase One Clustering in constructor "+ this.id);


           // start_phase_one_clustering();



        }catch (Exception e){
           e.printStackTrace();
            logger.error("Error:::",e);
        }


    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getParent_Id() {
        return parent_Id;
    }

    public void setParent_Id(String parent_Id) {
        this.parent_Id = parent_Id;
    }

    public String getIp_address() {
        return ip_address;
    }

    public void setIp_address(String ip_address) {
        this.ip_address = ip_address;
    }



    public void get_Neighbors(){
        logger.info(" get_Neighbors "+ this.id);
        String rack_row= rack_location.split(",")[0];
        String rack_column= rack_location.split(",")[0];
        List<String> my_Neighbors_Rack = new ArrayList<String>();
        my_Neighbors_Rack.add(""+String.valueOf(Integer.parseInt(rack_row)+1)+","+String.valueOf(Integer.parseInt(rack_column))+"");
        my_Neighbors_Rack.add(""+String.valueOf(Integer.parseInt(rack_row)-1)+","+String.valueOf(Integer.parseInt(rack_column))+"");
        my_Neighbors_Rack.add(""+String.valueOf(Integer.parseInt(rack_row))+","+String.valueOf(Integer.parseInt(rack_column)+1)+"");
        my_Neighbors_Rack.add(""+String.valueOf(Integer.parseInt(rack_row))+","+String.valueOf(Integer.parseInt(rack_column)-1)+"");
        my_Neighbors_Rack.add(""+String.valueOf(Integer.parseInt(rack_row)+1)+","+String.valueOf(Integer.parseInt(rack_column)+1)+"");
        my_Neighbors_Rack.add(""+String.valueOf(Integer.parseInt(rack_row)-1)+","+String.valueOf(Integer.parseInt(rack_column)-1)+"");
        my_Neighbors_Rack.add(""+String.valueOf(Integer.parseInt(rack_row)+1)+","+String.valueOf(Integer.parseInt(rack_column)-1)+"");
        my_Neighbors_Rack.add(""+String.valueOf(Integer.parseInt(rack_row)-1)+","+String.valueOf(Integer.parseInt(rack_column)+1)+"");

        for(String el: my_Neighbors_Rack){
            try{
                BasicDBObject query = new BasicDBObject();
                query.put("rack_location",el );

                DBObject document = collection.findOne(query);;
                if(document!=null)
                  this.neighbor_ID.add((String) document.get("parent_Id"));
                else
                    logger.info("Node: "+id+"- No node with rackLocation:"+el+" found!");

            }catch(Exception e){
                System.out.println(e);
            }
        }

    }



  public void send_size_to_parent(){
        if(this.parent_Id!=null){
            client.startStageOneCluster(this,nodeIdsList.getNodeIdsList().get(this.parent_Id));
        }else{
            logger.info("Node: %s - Setting myself as clusterhead as no parent found! "+id);
            this.is_Cluster_head=1;
            this.cluster_head_Id= this.id;
            this.state = "free";
            BasicDBObject query = new BasicDBObject();
            query.put("node_id", this.id);
            try{
                logger.info("Node: %s - Updating DB with size,hopcount variables "+id+" "+this.size);
                BasicDBObject newDocument = new BasicDBObject();
                newDocument.put("is_Cluster_head", this.is_Cluster_head);
                newDocument.put("cluster_head_Id", this.cluster_head_Id);
                newDocument.put("parent_Id", null);
                newDocument.put("size", this.size);
                newDocument.put("hop_count", 0);
                newDocument.put("state", this.state);



                BasicDBObject updateObject = new BasicDBObject();
                updateObject.put("$set", newDocument);

                collection.update(query, updateObject);

                logger.info("Node: %s - Successfully DB with size,hopcount variables");
            }catch(Exception e){
                logger.error("Some Error occurred in sendSizeToParent()");
               System.out.println(e);
            }
            client.sendCluster(this);
        }
  }



    public void setChild_list_Id(List<String> child_list_Id) {
        this.child_list_Id = child_list_Id;
    }

    public int getDist() {
        return dist;
    }

    public void setDist(int dist) {
        this.dist = dist;
    }

    public String getCluster_head_Id() {
        return cluster_head_Id;
    }

    public void setCluster_head_Id(String cluster_head_Id) {
        this.cluster_head_Id = cluster_head_Id;
    }

    public int getHop_count() {
        return hop_count;
    }

    public void setHop_count(int hop_count) {
        this.hop_count = hop_count;
    }

    public String getRack_location() {
        return rack_location;
    }

    public void setRack_location(String rack_location) {
        this.rack_location = rack_location;
    }

    public List<String> getSub_tree_list() {
        return sub_tree_list;
    }

    public void setSub_tree_list(List<String> sub_tree_list) {
        this.sub_tree_list = sub_tree_list;
    }

    public List<String> getNeighbour_list() {
        return neighbour_list;
    }

    public void setNeighbour_list(List<String> neighbour_list) {
        this.neighbour_list = neighbour_list;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getChild_request_counter() {
        return child_request_counter;
    }

    public void setChild_request_counter(int child_request_counter) {
        this.child_request_counter = child_request_counter;
    }

    public int getInitial_node_child_length() {
        return initial_node_child_length;
    }

    public void setInitial_node_child_length(int initial_node_child_length) {
        this.initial_node_child_length = initial_node_child_length;
    }

    public String getBest_node_id() {
        return best_node_id;
    }

    public void setBest_node_id(String best_node_id) {
        this.best_node_id = best_node_id;
    }

    public int getBest_node_hop_count() {
        return best_node_hop_count;
    }

    public void setBest_node_hop_count(int best_node_hop_count) {
        this.best_node_hop_count = best_node_hop_count;
    }

    public String getBest_node_cluster_head_Id() {
        return best_node_cluster_head_Id;
    }

    public void setBest_node_cluster_head_Id(String best_node_cluster_head_Id) {
        this.best_node_cluster_head_Id = best_node_cluster_head_Id;
    }

    public List<String> getNeighbor_ID() {
        return neighbor_ID;
    }

    public void setNeighbor_ID(List<String> neighbor_ID) {
        this.neighbor_ID = neighbor_ID;
    }

    public String getShift_Node_Id() {
        return shift_Node_Id;
    }

    public void setShift_Node_Id(String shift_Node_Id) {
        this.shift_Node_Id = shift_Node_Id;
    }

    public int getShift_Node_Sum() {
        return shift_Node_Sum;
    }

    public void setShift_Node_Sum(int shift_Node_Sum) {
        this.shift_Node_Sum = shift_Node_Sum;
    }

    public int getShift_Node_Cluster() {
        return shift_Node_Cluster;
    }

    public void setShift_Node_Cluster(int shift_Node_Cluster) {
        this.shift_Node_Cluster = shift_Node_Cluster;
    }

    public int getIs_Cluster_head() {
        return is_Cluster_head;
    }

    public void setIs_Cluster_head(int is_Cluster_head) {
        this.is_Cluster_head = is_Cluster_head;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public HashSet<String> getNeighbour_Hello_Array() {
        return neighbour_Hello_Array;
    }

    public void setNeighbour_Hello_Array(HashSet<String> neighbour_Hello_Array) {
        this.neighbour_Hello_Array = neighbour_Hello_Array;
    }

    //    public void sendSizeToParent() {
//            logger.info("size sent to parent node");
//    }



    public void propogate_Cluster_head_Info(String cluster_name, int hop_count){
        if(this.child_list_Id!=null && this.child_list_Id.size()!=0) {
          client.propogate_Cluster_head_Info(this,cluster_name,hop_count+1);
        }

    }


    public void start_phase_one_clustering(){
        System.out.println("Node: %s - Starting Phase One Clustering "+ this.id);
        logger.info("Node: %s - Starting Phase One Clustering "+ this.id);
        logger.info("inside start phase one clustering in node" +this.child_list_Id);
        if(this.child_list_Id==null || this.child_list_Id.size()==0){
            logger.info("Node: %s - Calling phaseOneClusterStart with parentId: "+this.id+" "+this.parent_Id);
            client.startStageOneCluster(this,nodeIdsList.getNodeIdsList().get(this.parent_Id));
            System.out.println("Node: %s - Sent size() message to parent: %s" +this.id+" "+this.parent_Id);
            logger.info("Node: %s - Sent size() message to parent: " +this.id+" "+this.parent_Id);
        }else{
            logger.info("Node: %s - I am a parent not leaf "+ this.id);
        }

    }
}


