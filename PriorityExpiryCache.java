
/*
Implement an LRU cache which includes the following features:
  Expire Time - after which an entry in the cache is invalid
  Priority - lower priority entries should be evicted before higher priority entries

The Cache eviction strategy should be as follows:
  Evict expired entries first
  If there are no expired items to evict then evict the lowest priority entries
    Tie breaking among entries with the same priority is done via least recently used.

You can use any language.

Your task is to implement a PriorityExpiryCache cache with a max capacity.  Specifically please fill out the data structures on the PriorityExpiryCache object and implement the entry eviction method.

You do NOT need to implement the get or set methods.

It should support these operations:
  Get: Get the value of the key if the key exists in the cache and is not expired.
  Set: Update or insert the value of the key with a priority value and expiretime.
    Set should never ever allow more items than maxItems to be in the cache.
    When evicting we need to evict the lowest priority item(s) which are least recently used.

Example:
p5 => priority 5
e10 => expires at 10 seconds since epoch
*/

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.*;


class PriorityExpiryCache{
  
  int maxItems, g_Time;
  
  // TODO(interviewee): implement this
   
  class Node{
      String key;
      int val, pri, exp_time;
      Node prev, next;
    
      public Node(String key,int val,int pri,int exp_time){
        this.key = key;
        this.val = val;
        this.pri = pri;
        this.exp_time = exp_time;
      }
  }
  
  // Cache map - get = O(logN) - set = O(logN)
  ConcurrentHashMap<String,Node> cache;
  
  //pointers to start and end for customized linkedlist
  Node head, tail;

  //Expiry - evict = O(logN)
  ConcurrentHashMap<Integer,LinkedList<Node>> expiryCache;
    
  //Priority - 
  ConcurrentHashMap<Integer,LinkedList<Node>> priorityCache;
  
  public PriorityExpiryCache(int maxItems){
    //initialize the max size of the the cache
    this.maxItems = maxItems;
    
    //initialize the global time counter to keep track of the current time
    this.g_Time = 0;
    
    //creating the linkedlist with head and tail nodes
    head = new Node(null,-1,-1,-1);
    tail = new Node(null,-1,-1,-1);
    head.next = tail;
    tail.prev = head;
    
    //initialize the data structures to store all the cache elements
    cache = new ConcurrentHashMap<String,Node>();
    
    //initialize the data structures to store all the cache elements according to expiry time (grouping elements according to expiry time)
    expiryCache = new ConcurrentHashMap<Integer,LinkedList<Node>>();
    
    //initialize the data structures to store all the cache elements according to priority time (grouping elements according to priority time)
    priorityCache = new ConcurrentHashMap<Integer,LinkedList<Node>>();
    
  }
  
  
  public synchronized int get(String key) {
    // ... the interviewee does not need to implement this now.
    // Assume that this will return the value for the key in the cache
    
    //make a call to evict items to delete any expired nodes
    EvictItems();
    
    //return the value for the key is its present in the cache else return -1 for invalid get request
    if(cache.containsKey(key)){
        Node node = cache.get(key);
        addToTop(node);
        return node.val;
    }
    return -1;
  }
  
  public synchronized void set(String key, int value, int priority, int expiryInSecs) {
    // ... the interviewee does not need to implement this now.
    // Assume that this will add this key, value pair to the cache
    Node node = cache.getOrDefault(key,null);
    if(node==null){
      if(maxItems == cache.size())
          EvictItems();
      
      //create a new node
      Node newNode = new Node(key,value,priority,expiryInSecs);
      
      //add it to linkedlist
      addNode(newNode);
      
      //add it to cache
      cache.put(key,newNode);
      
    }else{
      
        //evict if change in exp time - O(1)
        if(node.exp_time!=expiryInSecs){
            deleteNodeFromCaches(node.exp_time,node,expiryCache);
        }
      
        //evict if change in exp time - O(1)
        if(node.pri!=priority){
          deleteNodeFromCaches(node.pri,node,priorityCache);
        }
      
        node.exp_time = expiryInSecs;
        node.val = value;
        node.pri = priority;
      
        //move it to the top of the linkedlist
        addToTop(node);
        
        //add update node to cache
        cache.put(key,node);
        
    }
    
  }
  
  //change the max capacity of the cache
  public synchronized void SetMaxItems(int numItems){
    maxItems = numItems;
    EvictItems();
  }

  
  public synchronized void DebugPrintKeys() {
    // ... the interviewee does not need to implement this now.
    // Prints all the keys in the cache for debugging purposes
    System.out.println(cache.keySet());
  }
  
  public synchronized void EvictItems() {
    // TODO(interviewee): implement this
    
    //find all the expired nodes and delete them
    for(int exp:expiryCache.keySet()){
      if(this.g_Time >= exp){
          evictList(expiryCache.get(exp),expiryCache.get(exp).size());
          expiryCache.remove(exp);
      }
    }
    //if cache size is less then maxItems then don't delete any other nodes.
    if(cache.size()<=maxItems)
      return ;
    
    /*find the difference between the cache size and maxItems to find the number of nodes to delete*/
    int diff = cache.size() - maxItems;
    
    //find the minimum priority in the map - O(1) and evict all min priority items 
    while(diff>0 && !priorityCache.isEmpty()){
      
        int min = Collections.min(priorityCache.keySet());
        int size = priorityCache.get(min).size();
        //if size of the queue is less then diff then delete all the nodes
        if(size<=diff){
          evictList(priorityCache.get(min),size);
          priorityCache.remove(min);
          diff = diff-size;
        }
      
        else{    //delete only the number of nodes that make up the diff
          evictList(priorityCache.get(min),diff);
          diff = 0;
        }
      
      }
  }
   
  //Evict all the marked nodes that either expired or are with lowest priority
    public synchronized void evictList(Deque<Node> dq,int size){
        while(!dq.isEmpty() && size>0){
            Node deleteNode = dq.removeLast();
            deleteNode(deleteNode);
            cache.remove(deleteNode.key);
            size--;
        }  
    } 
  
  
  //add the new node to the top of the list
    public synchronized void addNode(Node node){

        node.next = head.next;
        node.prev = head;

        head.next.prev = node;
        head.next = node;

        //add it to the expiry map
        addNodeToCaches(node.exp_time,node,expiryCache);

        //add it to priority map
        addNodeToCaches(node.pri,node,priorityCache);
    }

    //delete the node no longer required
    public synchronized void deleteNode(Node node){

        Node next = node.next;
        Node prev = node.prev;

        next.prev = prev;
        prev.next = next;

        //add it to the expiry map
        deleteNodeFromCaches(node.exp_time,node,expiryCache);

        //add it to priority map
        deleteNodeFromCaches(node.pri,node,priorityCache);
    }

    //bring the most recently used node to the top
    public synchronized void addToTop(Node node){
        deleteNode(node);
        addNode(node);
    }
    
    //evict node from the above selected cache
  public synchronized void deleteNodeFromCaches(int key, Node node,
                                          ConcurrentHashMap<Integer,LinkedList<Node>> caches){
      LinkedList<Node> list = caches.get(key);
      list.remove(node);
      caches.put(key,list);
  }
  
  //add node to the above selected cache
  public synchronized void addNodeToCaches(int key, Node node,
                                           ConcurrentHashMap<Integer,LinkedList<Node>> caches){
      LinkedList<Node> list = caches.getOrDefault(key, 
                                                    new LinkedList<Node>());
      list.addFirst(node);
      caches.put(key,list);
  }
}


class Solution{
  public static void main(String[] args) {
    
      ReentrantLock lock = new ReentrantLock(true);
    
      final PriorityExpiryCache c = new PriorityExpiryCache(5);
      
      //adding the data into the cache
      try{
        lock.lock();
        c.set("A", 1, 5,  100 );
        c.set("B", 2, 15, 3   );
        c.set("C", 3, 5,  10  );
        c.set("D", 4, 1,  15  );
        c.set("E", 5, 5,  150 );
      }catch(Exception e){
        System.out.print("Exception" + e);
      }finally{
        lock.unlock();
      }
    
      //getting the value for a given key
      try{
        lock.lock();
        int val = c.get("C");
        if(val==-1)
          System.out.print("No item with key C exists in the cache.");
        else
          System.out.println("The value for key C = " +c.get("C"));
      }catch(Exception e){
        System.out.print("Exception" + e);
      }finally{
        lock.unlock();
      }
    
      //changing the size of the cache to 5
      try{
        lock.lock();
        // Current Time = 0
        c.SetMaxItems(5);
        // Keys in Cache = ["A", "B", "C", "D", "E"]
        // space for 5 keys, all 5 items are included
        c.DebugPrintKeys();
      }catch(Exception e){
        System.out.print("Exception" + e);
      }finally{
        lock.unlock();
      }
    
      //changing the Current Time to 5
      try{
        lock.lock();
        c.g_Time += 5;
      }catch(Exception e){
        System.out.print("Exception" + e);
      }finally{
        lock.unlock();
      }
      
    
      //getting the value for a given key
      try{
        lock.lock();
        int val = c.get("B");
        if(val==-1)
          System.out.println("No item with key B exists in the cache.");
        else
          System.out.println("The value for key B = " +c.get("B"));
      }catch(Exception e){
        System.out.print("Exception" + e);
      }finally{
        lock.unlock();
      }
    
      
      //changing the max size of the cache to 4
      try{
        lock.lock();
        // Current Time = 5
        c.SetMaxItems(4);
        // Keys in Cache = ["A", "C", "D", "E"]
        // space for 4 keys, all 4 items are included
        c.DebugPrintKeys();
      }catch(Exception e){
        System.out.print("Exception" + e);
      }finally{
        lock.unlock();
      }
    
      //changing the max size of the cache to 3
      try{
        lock.lock();
        // Current Time = 5
        c.SetMaxItems(3);
        // Keys in Cache = ["A", "C", "E"]
        // space for 3 keys, all 3 items are included
        c.DebugPrintKeys();
      }catch(Exception e){
          System.out.print("Exception"+e);
      }finally{
        lock.unlock();
      }
    
      //changing the max size of the cache to 2
      try{
        lock.lock();
        // Current Time = 5
        c.SetMaxItems(2);
        // Keys in Cache = ["A","C"]
        // space for 2 keys, all 2 items are included
        c.DebugPrintKeys();
      }
      catch(Exception e){
        System.out.print("Exception");
      }finally{
        lock.unlock();
      }
      
      //changing the max size of the cache to 1
      try{
        lock.lock();
        // Current Time = 5
        c.SetMaxItems(1);
        // Keys in Cache = ["C"]
        // space for 1 keys, all 1 items are included
        c.DebugPrintKeys();
      }
      catch(Exception e){
        System.out.print("Exception");
      }
      finally{
        lock.unlock();
      }
  } 
}
