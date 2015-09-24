package org.sead.cp.demo;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;

public class MongoDB {

	static public MongoClient mongoClientInstance = null;
	public static String researchobjects = "researchobjects";
	public static String people="people";
	public static String oreMaps="oreMaps";
	public static String repositories = "repositories";
	public static synchronized MongoClient getMongoClientInstance() {
	    if (mongoClientInstance == null) {
	        try {
	            mongoClientInstance = new MongoClient();
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }
	    return mongoClientInstance;
	}
	static public MongoDatabase getServicesDB() {
		MongoDatabase db = getMongoClientInstance().getDatabase("seadcp");
		return db;
	}
}
