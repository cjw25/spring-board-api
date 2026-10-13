package com.example.board;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class BoardService {
    private final PostRepository posts;
    private final CommentRepository comments;
    private final MemberRepository members;
    BoardService(PostRepository posts, CommentRepository comments, MemberRepository members) {
        this.posts = posts; this.comments = comments; this.members = members;
    }
    public Dto.PageView<Dto.PostView> list(int page, int size, String query) {
        String escaped = query.replace("!","!!").replace("%","!%").replace("_","!_");
        var result = posts.list(escaped, PageRequest.of(page,size));
        return new Dto.PageView<>(result.getContent(),page,size,result.getTotalElements(),result.getTotalPages());
    }
    public Dto.PostView detail(Long id) { return view(post(id)); }
    @Transactional
    public Dto.PostView create(Dto.PostInput input, String email) { return view(posts.save(new Post(input.title(),input.content(),member(email)))); }
    @Transactional
    public Dto.PostView update(Long id, Dto.PostInput input, String email) {
        Post post = post(id); own(post.author,email); post.update(input.title(),input.content()); return view(post);
    }
    @Transactional
    public void delete(Long id, String email) {
        Post post = post(id); own(post.author,email);
        comments.deleteAllForPost(id); posts.delete(post);
    }
    public List<Dto.CommentView> comments(Long postId) {
        if (!posts.existsById(postId)) throw ApiException.missing("Post");
        return comments.findByPostIdOrderByCreatedAtAscIdAsc(postId).stream().map(Dto.CommentView::of).toList();
    }
    @Transactional
    public Dto.CommentView comment(Long postId, Dto.CommentInput input, String email) {
        return Dto.CommentView.of(comments.save(new Comment(input.content(),member(email),post(postId))));
    }
    @Transactional
    public Dto.CommentView updateComment(Long id, Dto.CommentInput input, String email) {
        Comment comment = comment(id); own(comment.author,email); comment.update(input.content()); return Dto.CommentView.of(comment);
    }
    @Transactional
    public void deleteComment(Long id, String email) { Comment c = comment(id); own(c.author,email); comments.delete(c); }
    private Post post(Long id) { return posts.findWithAuthorById(id).orElseThrow(() -> ApiException.missing("Post")); }
    private Comment comment(Long id) { return comments.findWithAuthorById(id).orElseThrow(() -> ApiException.missing("Comment")); }
    private Member member(String email) { return members.findByEmail(email).orElseThrow(() -> ApiException.missing("Member")); }
    private void own(Member author, String email) { if (!author.email.equals(email)) throw ApiException.forbidden(); }
    private Dto.PostView view(Post p) { return new Dto.PostView(p.id,p.title,p.content,p.author.id,p.author.nickname,p.createdAt,p.updatedAt,comments.countByPostId(p.id)); }
}
