package com.example.demo.exception;

public class MenuItemNotFoundException extends RuntimeException {
    public MenuItemNotFoundException(Long id) {
        super("Блюдо с id=" + id + " не найдено");
    }
}
