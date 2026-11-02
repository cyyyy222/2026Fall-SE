package com.campus.ticketing.service.core;

import static com.campus.ticketing.common.EntityRecords.*;
import static com.campus.ticketing.service.core.BusinessSupport.*;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.entity.*;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class FileUseCases {
  private final BusinessSupport b;
  private final EventUseCases events;
  private final ControlledFiles files;

  public FileUseCases(BusinessSupport b, EventUseCases events, ControlledFiles files) {
    this.b = b;
    this.events = events;
    this.files = files;
  }

  public UseCaseResult<Mutation<Views.EventManage>> replacePoster(
      RequestContext c, UUID eventId, Requests.PosterUpload r) {
    return b.write(
        () -> {
          b.actor(c, Role.ORGANIZER);
          b.eventAccess(c, eventId, true, false);
          var x =
              b.change(
                  c,
                  "ORG-05",
                  eventId,
                  r,
                  "EVENT",
                  i -> events.manage(b.need(EventEntity.class, i, false)),
                  () -> {
                    var e = b.need(EventEntity.class, eventId, true);
                    editable(e, r.expectedVersion());
                    String mime = files.imageType(r.bytes());
                    var file = files.store(c.actor().userId(), "POSTER", r.bytes(), mime);
                    b.db().insert(file);
                    b.db()
                        .save(
                            copy(
                                e,
                                "posterFileId",
                                file.id(),
                                "version",
                                e.version() + 1,
                                "updatedAtMs",
                                b.now()));
                    return eventId;
                  });
          return ok(x.mutation());
        });
  }

  private void editable(EventEntity e, long version) {
    EventUseCases.version(e.version(), version);
    require(
        e.status().equals("DRAFT") || e.status().equals("PUBLISHED") && b.effectiveEnd(e) == null,
        "STATE_CONFLICT",
        "当前活动不可编辑海报");
  }

  public UseCaseResult<Mutation<Views.EventManage>> removePoster(
      RequestContext c, UUID eventId, Queries.Version q) {
    return b.write(
        () -> {
          b.actor(c, Role.ORGANIZER);
          b.eventAccess(c, eventId, true, false);
          var x =
              b.change(
                  c,
                  "ORG-06",
                  eventId,
                  q,
                  "EVENT",
                  i -> events.manage(b.need(EventEntity.class, i, false)),
                  () -> {
                    var e = b.need(EventEntity.class, eventId, true);
                    editable(e, q.expectedVersion());
                    b.db()
                        .save(
                            copy(
                                e,
                                "posterFileId",
                                null,
                                "version",
                                e.version() + 1,
                                "updatedAtMs",
                                b.now()));
                    return eventId;
                  });
          return ok(x.mutation());
        });
  }

  public DownloadResult publicPoster(RequestContext c, UUID eventId) {
    return b.read(
        () -> {
          var e = b.need(EventEntity.class, eventId, false);
          b.publicEvent(e);
          return poster(e);
        });
  }

  public DownloadResult managementPoster(RequestContext c, UUID eventId) {
    return b.read(() -> poster(b.eventAccess(c, eventId, false, false)));
  }

  private DownloadResult poster(EventEntity e) {
    if (e.posterFileId() == null) throw error(404, "RESOURCE_NOT_FOUND", "未设置海报");
    var file = b.need(StoredFileEntity.class, e.posterFileId(), false);
    return files.download(
        file,
        "poster-" + e.id() + (file.contentType().equals("image/png") ? ".png" : ".jpg"),
        false);
  }
}
