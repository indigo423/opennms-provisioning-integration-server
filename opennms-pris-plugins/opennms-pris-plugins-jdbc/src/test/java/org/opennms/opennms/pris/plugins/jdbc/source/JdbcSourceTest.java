package org.opennms.opennms.pris.plugins.jdbc.source;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opennms.pris.api.MockInstanceConfiguration;
import org.opennms.pris.model.MetaData;
import org.opennms.pris.model.Requisition;
import org.opennms.pris.model.RequisitionNode;

public class JdbcSourceTest {

    private Connection connection = null;
    private final String DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";
    private final String CONNECTION_URL_CREATE = "jdbc:derby:memory:testDB;create=true";
    private final String CONNECTION_URL_SHUTDOWN = "jdbc:derby:memory:testDB;shutdown=true;";
    private final String CONNECTION_URL_DROP = "jdbc:derby:memory:testDB;drop=true";

    private final String INSTANCE = "Test-Instance";
    private final String SQL_CREATE_ALL = "CREATE TABLE node ("
            + "foreignId INT,"
            + "nodeLabel VARCHAR(255),"
            + "location VARCHAR(255),"
            + "parentNodeLabel VARCHAR(255),"
            + "parentForeignId VARCHAR(255),"
            + "parentForeignSource VARCHAR(255),"
            + "ipAddress VARCHAR(255),"
            + "ifType CHAR(1),"
            + "ifStatus VARCHAR(255),"
            + "description VARCHAR(255),"
            + "city VARCHAR(255),"
            + "state VARCHAR(255),"
            + "serviceName VARCHAR(255),"
            + "categoryName VARCHAR(255),"
            + "metaDataColumn1 VARCHAR(255),"
            + "metaDataColumn2 VARCHAR(255),"
            + "PRIMARY KEY (foreignId))";

    private final String SQL_SELECT_STATEMENT_TEST_1 = "SELECT foreignId AS Foreign_Id, "
            + "nodelabel AS Node_Label,"
            + "location AS Location,"
            + "parentNodeLabel AS Parent_Node_Label,"
            + "parentForeignId AS Parent_Foreign_Id,"
            + "parentForeignSource AS Parent_Foreign_Source,"
            + "nodelabel AS Node_Label,"
            + "nodelabel AS Node_Label,"
            + "ipAddress AS IP_Address,"
            + "ifType AS If_Type,"
            + "ifStatus AS InterfaceStatus,"
            + "description AS Asset_Description,"
            + "city AS Asset_City,"
            + "state AS Asset_State,"
            + "serviceName AS Svc,"
            + "metaDataColumn1 AS \"MetaData_keyWithoutContext\","
            + "metaDataColumn2 AS \"MetaData_Context:keyWithContext\","
            + "categoryName AS Cat FROM node";

    @BeforeClass
    public static void setUpClass() {
        System.setProperty("derby.stream.error.field", "DerbyUtil.DEV_NULL");
    }

    @Before
    public void setUp() throws ClassNotFoundException, SQLException {
        Class.forName(DRIVER);
        connection = DriverManager.getConnection(CONNECTION_URL_CREATE);
        Statement stmnt = connection.createStatement();
        stmnt.executeUpdate(SQL_CREATE_ALL);

        insertRow("1", "node1", null, null, null, null, "192.168.0.1", "P", "1", "description1", "city1", "state1", "service1", "category1", "Foo1", "Bar1");
        insertRow("2", "node2", "Test-Location", "ParentNodeLabel", "", "", "192.168.0.2", "P", "3", "description2", "city2", "state2", "service2", "category2", "Foo2", "Bar2");
        insertRow("3", "node3", null, null, "ParentId", "ParentSouce", "192.168.0.3", "P", "1", "description3", "city3", "state3", "service3", "category3", "Foo3", "Bar3");
    }

    private void insertRow(String foreignId, String nodeLabel, String location, String parentNodeLabel, String parentForeignId, String parentForeignSource, String ipAddress, String ifType, String ifStatus, String description, String city, String state, String serviceName, String categoryName, String metaDataColumn1, String metaDataColumn2) throws SQLException {
        String DML = "INSERT INTO node (foreignId, nodeLabel, location, parentNodeLabel, parentForeignId, parentForeignSource, ipAddress, ifType, ifStatus, description, city, state, serviceName, categoryName, metaDataColumn1, metaDataColumn2) VALUES (" + foreignId + ", '" + nodeLabel + "', '" + location + "', '" + parentNodeLabel + "', '" + parentForeignId + "', '" + parentForeignSource + "', '" + ipAddress + "', '" + ifType + "', '" + ifStatus + "', '" + description + "', '" + city + "', '" + state + "', '" + serviceName + "', '" + categoryName + "', '" + metaDataColumn1 +"', '" + metaDataColumn2 +"')";
        Statement stmnt = connection.createStatement();
        stmnt.executeUpdate(DML);
    }

    @After
    public void cleanUp() {
        try {
            connection.close();
            DriverManager.getConnection(CONNECTION_URL_DROP);
            DriverManager.getConnection(CONNECTION_URL_SHUTDOWN);
        } catch (SQLException ex) {
        }
    }

    @Test
    public void testDB() throws SQLException {
        System.out.println("testDB");
        Statement selectAll = connection.createStatement();
        ResultSet selectAllResult = selectAll.executeQuery(SQL_SELECT_STATEMENT_TEST_1);
        while (selectAllResult.next()) {
            System.out.println("Results: " + selectAllResult.getString("Foreign_Id") + " " + selectAllResult.getString("Node_Label")  + " " + selectAllResult.getString("Location"));
        }
    }

    @Test
    public void testDump() {
        MockInstanceConfiguration config = new MockInstanceConfiguration(INSTANCE);
        config.set("driver", DRIVER);
        config.set("url", CONNECTION_URL_CREATE);
        config.set("user", "");
        config.set("password", "");
        config.set("selectStatement", SQL_SELECT_STATEMENT_TEST_1);

        JdbcSource jdbcSource = new JdbcSource(config);
        Requisition requisition = (Requisition) jdbcSource.dump();
        Assert.assertEquals(INSTANCE, requisition.getForeignSource());
        Assert.assertEquals(3, requisition.getNodes().size());
        Assert.assertEquals("Test-Location", requisition.getNodes().get(1).getLocation());
        Assert.assertEquals("ParentNodeLabel", requisition.getNodes().get(1).getParentNodeLabel());

        final RequisitionNode node1 = requisition.getNodes().stream().filter(n -> n.getForeignId().equals("1")).findFirst().get();
        final RequisitionNode node2 = requisition.getNodes().stream().filter(n -> n.getForeignId().equals("2")).findFirst().get();
        final RequisitionNode node3 = requisition.getNodes().stream().filter(n -> n.getForeignId().equals("3")).findFirst().get();

        Assert.assertThat(node1.getMetaDatas(), Matchers.containsInAnyOrder(new MetaData("requisition", "keyWithoutContext", "Foo1"), new MetaData("Context", "keyWithContext", "Bar1")));
        Assert.assertThat(node2.getMetaDatas(), Matchers.containsInAnyOrder(new MetaData("requisition", "keyWithoutContext", "Foo2"), new MetaData("Context", "keyWithContext", "Bar2")));
        Assert.assertThat(node3.getMetaDatas(), Matchers.containsInAnyOrder(new MetaData("requisition", "keyWithoutContext", "Foo3"), new MetaData("Context", "keyWithContext", "Bar3")));
    }
}