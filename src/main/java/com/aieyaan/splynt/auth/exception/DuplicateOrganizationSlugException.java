package com.aieyaan.splynt.auth.exception;

public class DuplicateOrganizationSlugException
        extends RuntimeException {

    public DuplicateOrganizationSlugException(String slug) {
        super(
                "An organization already exists with slug "
                        + slug
        );
    }
}