package com.example.board;

import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    @EntityGraph(attributePaths = {"author", "post"})
    List<Comment> findByPostIdOrderByCreatedAtAscIdAsc(Long postId);
    @EntityGraph(attributePaths = {"author", "post"})
    Optional<Comment> findWithAuthorById(Long id);
    long countByPostId(Long postId);
    @Modifying @Query("delete from Comment c where c.post.id = :postId")
    void deleteAllForPost(Long postId);
}
