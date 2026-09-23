package com.example.helloapplication;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HomeViewTextTest {

    @Test
    void namesAreQuotedOnlyWhenTheyNeedIt() {
        assertEquals("orders", Home_View.sqlName("orders"));
        assertEquals("Order_Items2", Home_View.sqlName("Order_Items2"));
        assertEquals("\"order items\"", Home_View.sqlName("order items"));
        assertEquals("\"2024_sales\"", Home_View.sqlName("2024_sales"));
        assertEquals("\"say \"\"hi\"\"\"", Home_View.sqlName("say \"hi\""));
        assertEquals("\"order\"", Home_View.sqlName("order"));
        assertEquals("\"Group\"", Home_View.sqlName("Group"));
    }

    @Test
    void starterQuerySelectsTheWholeTable() {
        assertEquals("SELECT * FROM customers", Home_View.starterQuery("customers"));
        assertEquals("SELECT * FROM \"order\"", Home_View.starterQuery("order"));
    }

    @Test
    void openedMessageSaysReadOnly() {
        assertEquals("Viewing customers, orders (read-only)", Home_View.openedMessage("customers, orders"));
    }
}
