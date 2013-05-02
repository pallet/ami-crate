## Usage

Builds an S3 backed AMI from a running instance.  Installs ruby and ami-tools,
and builds an image that excludes these.

The `make-ami` function takes a group-spec and a map of options to pass to the
ami `settings` function, and builds an AMI using a new node.

The `make-ami-from-group-node` function takes a group name and a map of options
to pass to the ami `settings` function, and builds an AMI using an existing node
running for the specified group name.

The `server-spec` function provides a convenient pallet server spec for
ami-crate.  It takes a single map as an argument, specifying configuration
choices, as described below for the `settings` function.  You can use this
in your own group or server specs in the :extends clause.

```clj
(require '[pallet.crate.ami :as ami])
(server-spec
  :extends [(ami/server-spec
             {:image-name "pallet-test-ami"
              :image-description "Pallet test AMI"
              :s3-bucket <your-s3-bucket>
              :s3-path <your-s3-path>
              :user-id <your-aws-user-id>
              :private-key-source {:local-file <your-aws-private-key-file>}
              :certificate-source {:local-file <your-aws-cert-file>}})])
```

While `server-spec` provides an all-in-one function, you can use the individual
plan functions as you see fit.  The `server-spec` provides `:install`,
`:configure`, `:ami-bundle`, `:ami-upload`, `:ami-register` and `cleanup`
phases.

The `settings` function provides a plan function that should be called in the
`:settings` phase.  The function puts the configuration options into the pallet
session, where they can be found by the other crate functions, or by other
crates wanting to interact with ami-crate.

The `install` function is responsible for installing and.

The `configure` function writes the credentials to the remote node..

The `ami-bundle` function builds an image bundle.

The `ami-upload` function uploads an image bundle to S3.

The `ami-register` function registers an AMI from an image bundle stored in S3.

The `cleanup` function removes installed ami-tools, ruby and generated image
bundle files.

## Testing

The crate live test is based on loading a `test_credentials.clj` file to provide
a credential map that is merged into the ami settings.
