package org.example.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.example.bean.Book;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
@Tag(name = "书籍查询")
public class BookController {


    @GetMapping("/search/title")
    public ResponseEntity<List<Book>> searchBooksByTitle(@Parameter(name = "标题") @RequestParam String title) {
        List<Book> books = new ArrayList<>();
        books.add(new Book(1L, title, "Spring", "Spring Team", null, null));
        return ResponseEntity.ok(books);
    }

    @GetMapping("/search/author")
    public ResponseEntity<List<Book>> searchBooksByAuthor(@RequestParam String author) {
        List<Book> books = new ArrayList<>();
        books.add(new Book(1L, "标题", "Spring", author, null, null));
        return ResponseEntity.ok(books);
    }

    @GetMapping("/search/category")
    public ResponseEntity<List<Book>> searchBooksByCategory(@RequestParam String category) {
        List<Book> books = new ArrayList<>();
        books.add(new Book(1L, "标题", category, "Spring Team", null, null));
        return ResponseEntity.ok(books);
    }
}
