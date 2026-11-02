package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.PosterService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class PosterServiceImpl implements PosterService {
  private final BusinessSupport support;
  private final FileUseCases fileUseCases;

  public PosterServiceImpl(BusinessSupport support, FileUseCases fileUseCases) {
    this.support = support;
    this.fileUseCases = fileUseCases;
  }

  @Override
  public DownloadResult managementPoster(RequestContext context, UUID eventId) {
    return fileUseCases.managementPoster(context, eventId);
  }
}
