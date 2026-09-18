package android.os.storage;

import android.os.storage.operations.FileOperationResult;
import android.os.storage.operations.FileOperationRequest;
import android.os.storage.operations.FileOperationEnqueueResult;

/** @hide */
interface IFileService {
    FileOperationEnqueueResult enqueueOperation(in FileOperationRequest request, in String packageName);
    FileOperationResult fetchResult(in String requestId);
    void registerCompletionListener(in String requestId, in String packageName);
    void unregisterCompletionListener(in String requestId);
}
