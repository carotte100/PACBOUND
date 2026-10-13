package com.maxim.paclearn.storage.utility

interface Storage<T> {
    fun insert(key: String, obj: T)
    fun size(): Int
    fun find(key: String): T?
    fun findAll(): List<T>
    fun update(key: String, obj: T)
    fun delete(key: String)
}

