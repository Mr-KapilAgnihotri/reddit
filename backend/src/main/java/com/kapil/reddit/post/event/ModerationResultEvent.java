package com.kapil.reddit.post.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Event consumed from the {@code post-moderated} topic (published by the Python ML service).
 *
 * <p>BUG FIX: with Lombok, a {@code boolean isModerated} field produces
 * {@code isModerated()} / {@code setModerated()}, which Jackson maps to a JSON property
 * called {@code "moderated"}. Python sends {@code "isModerated"}, so the flag was always
 * {@code false}. The explicit {@link JsonProperty} below binds the correct JSON name while
 * keeping the {@code isModerated()} / {@code setModerated()} Java API used by the tests.
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModerationResultEvent {

    private Long postId;

    private String maskedText;

    /** Present only when the post had a title (masked version of it). */
    private String maskedTitle;

    @Getter(onMethod_ = @JsonProperty("isModerated"))
    @Setter(onMethod_ = @JsonProperty("isModerated"))
    private boolean isModerated;

    private List<String> categories = new ArrayList<>();

    private Double severity;

    private String reason;

    private String provider;

    /** Convenience constructor kept for backwards compatibility with existing code/tests. */
    public ModerationResultEvent(Long postId, String maskedText, boolean isModerated) {
        this.postId = postId;
        this.maskedText = maskedText;
        this.isModerated = isModerated;
    }
}
