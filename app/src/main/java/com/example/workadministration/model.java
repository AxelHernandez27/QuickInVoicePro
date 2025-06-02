package com.example.workadministration;

import java.util.List;

public class model {
    public class Producto {
        public String nombre;
        public int cantidad;
        public double precio;

        public Producto(String nombre, int cantidad, double precio) {
            this.nombre = nombre;
            this.cantidad = cantidad;
            this.precio = precio;
        }
    }

    public class Ticket {
        public String cliente;
        public List<Producto> productos;
        public double total;
        public String notas;

        public Ticket(String cliente, List<Producto> productos, double total, String notas) {
            this.cliente = cliente;
            this.productos = productos;
            this.total = total;
            this.notas = notas;
        }
    }

}
