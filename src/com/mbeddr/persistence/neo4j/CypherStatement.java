package com.mbeddr.persistence.neo4j;

import org.neo4j.driver.v1.Statement;

/**
 * Created by kdummann on 27/10/2016.
 */
public interface CypherStatement {
    Statement toStatement();
    default boolean needsNodesToBeCreated() { return false; }
}
