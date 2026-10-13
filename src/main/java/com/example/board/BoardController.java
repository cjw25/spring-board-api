package com.example.board;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
public class BoardController {
    private final BoardService service;
    BoardController(BoardService service) { this.service = service; }
    @GetMapping("/posts")
    Dto.PageView<Dto.PostView> list(@RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,
                                  @RequestParam(defaultValue="20") @Min(1) @Max(100) int size,
                                  @RequestParam(defaultValue="") @Size(max=200) String q) { return service.list(page,size,q); }
    @GetMapping("/posts/{id}") Dto.PostView detail(@PathVariable @Positive Long id) { return service.detail(id); }
    @PostMapping("/posts")
    ResponseEntity<Dto.PostView> create(@Valid @RequestBody Dto.PostInput input, Authentication auth) {
        var post = service.create(input,auth.getName());
        return ResponseEntity.created(URI.create("/api/posts/"+post.id())).body(post);
    }
    @PutMapping("/posts/{id}")
    Dto.PostView update(@PathVariable @Positive Long id, @Valid @RequestBody Dto.PostInput input, Authentication auth) { return service.update(id,input,auth.getName()); }
    @DeleteMapping("/posts/{id}")
    ResponseEntity<Void> delete(@PathVariable @Positive Long id, Authentication auth) { service.delete(id,auth.getName()); return ResponseEntity.noContent().build(); }
    @GetMapping("/posts/{id}/comments")
    List<Dto.CommentView> comments(@PathVariable @Positive Long id) { return service.comments(id); }
    @PostMapping("/posts/{id}/comments")
    ResponseEntity<Dto.CommentView> comment(@PathVariable @Positive Long id, @Valid @RequestBody Dto.CommentInput input, Authentication auth) {
        var comment = service.comment(id,input,auth.getName());
        return ResponseEntity.created(URI.create("/api/posts/"+id+"/comments")).body(comment);
    }
    @PutMapping("/comments/{id}")
    Dto.CommentView updateComment(@PathVariable @Positive Long id, @Valid @RequestBody Dto.CommentInput input, Authentication auth) { return service.updateComment(id,input,auth.getName()); }
    @DeleteMapping("/comments/{id}")
    ResponseEntity<Void> deleteComment(@PathVariable @Positive Long id, Authentication auth) { service.deleteComment(id,auth.getName()); return ResponseEntity.noContent().build(); }
}
