package org.cleargram.integration;

/** Receives an already classified immutable result without Core or storage dependencies. */
interface DuplicateVideoPresentationConsumer {

    void onDuplicateVideoPresentation(DuplicateVideoCoordinatorResult result);
}
