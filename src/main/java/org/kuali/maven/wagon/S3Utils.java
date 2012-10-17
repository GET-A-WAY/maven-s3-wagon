/**
 * Copyright 2010-2012 The Kuali Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.opensource.org/licenses/ecl2.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kuali.maven.wagon;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;

public class S3Utils {
	private static final int KILOBYTE = 1024;
	private static final int MEGABYTE = 1024 * KILOBYTE;
	private static final int MULTI_PART_UPLOAD_THRESHOLD = 100 * MEGABYTE;
	private static final Logger log = LoggerFactory.getLogger(S3Utils.class);

	/**
	 * Upload a single file to Amazon S3. If the file is larger than 100MB a multi-part upload is used. This splits the file into multiple
	 * smaller chunks with each chunk being uploaded in a different thread. Once all the threads have completed the file is reassembled on
	 * Amazon's side as a single file again.
	 */
	public static final void upload(File file, PutObjectRequest request, AmazonS3Client client, TransferManager manager) {
		// Store the file on S3
		if (file.length() < MULTI_PART_UPLOAD_THRESHOLD) {
			// Use normal upload for small files
			client.putObject(request);
		} else {
			log.debug("Blocking multi-part upload: " + file.getAbsolutePath());
			// Use multi-part upload for large files
			blockingMultiPartUpload(request, manager);
		}
	}

	/**
	 * Use this method to reliably upload large files by splitting it up into manageable chunks and using separate threads to upload each
	 * chunk. Amazon recommends using a multi-part upload on files larger than 100MB. When this method returns all of the upload threads
	 * that handle portions of the file have completed. The file has also been reassembled on Amazon S3 and is ready for use.
	 */
	public static final void blockingMultiPartUpload(PutObjectRequest request, TransferManager manager) {
		// Use multi-part upload for large files
		Upload upload = manager.upload(request);
		try {
			// Block and wait for the upload to finish
			upload.waitForCompletion();
		} catch (Exception e) {
			throw new AmazonS3Exception("Unexpected error uploading file", e);
		}
	}

}
