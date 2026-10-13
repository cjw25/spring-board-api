package com.example.board;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
    // Scalar DTO projection: author and comment counts are fetched together, never per post.
    @Query(value = """
        select new com.example.board.Dto$PostView(p.id,p.title,p.content,a.id,a.nickname,p.createdAt,p.updatedAt,count(c.id))
        from Post p join p.author a left join Comment c on c.post = p
        where (:q = '' or lower(p.title) like lower(concat('%',:q,'%')) escape '!' or lower(p.content) like lower(concat('%',:q,'%')) escape '!')
        group by p.id,p.title,p.content,a.id,a.nickname,p.createdAt,p.updatedAt
        order by p.createdAt desc,p.id desc
        """, countQuery = """
        select count(p) from Post p
        where (:q = '' or lower(p.title) like lower(concat('%',:q,'%')) escape '!' or lower(p.content) like lower(concat('%',:q,'%')) escape '!')
        """)
    Page<Dto.PostView> list(@Param("q") String query, Pageable pageable);
    @EntityGraph(attributePaths = "author")
    Optional<Post> findWithAuthorById(Long id);
}
