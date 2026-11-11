module.exports = async ({github, core}) => {
  const [owner, repo] = process.env.REPOSITORY_NAME.split('/');
  let release;
  try {
    const response = await github.rest.repos.getReleaseByTag({owner, repo, tag: process.env.RELEASE_NAME});
    release = response.data;

  } catch (error) {
    if (error.status !== 404) { throw error; }
  }

  const found = release && !release.draft && release.assets.some(asset =>
    asset.name === `${process.env.RELEASE_NAME}.tar.gz` && asset.state === 'uploaded'
  );
  core.setOutput('found', Boolean(found));
};
