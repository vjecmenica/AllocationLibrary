import { downloadTextFile } from './download-text-file';

describe('downloadTextFile', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should download the supplied text and clean up browser resources', async () => {
    let createdBlob: Blob | null = null;
    let downloadName: string | null = null;
    let clickedHref: string | null = null;
    let anchorWasAttached = false;
    vi.spyOn(URL, 'createObjectURL').mockImplementation((value) => {
      if (!(value instanceof Blob)) {
        throw new Error('Expected a Blob.');
      }
      createdBlob = value;
      return 'blob:download-test';
    });
    const revokeObjectUrl = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      downloadName = this.download;
      clickedHref = this.href;
      anchorWasAttached = document.body.contains(this);
    });

    downloadTextFile('exported content', 'result.csv', 'text/csv;charset=utf-8');

    expect(createdBlob).not.toBeNull();
    expect(await createdBlob!.text()).toBe('exported content');
    expect(createdBlob!.type).toBe('text/csv;charset=utf-8');
    expect(downloadName).toBe('result.csv');
    expect(clickedHref).toBe('blob:download-test');
    expect(anchorWasAttached).toBe(true);
    expect(click).toHaveBeenCalledOnce();
    expect(document.querySelector('a[download="result.csv"]')).toBeNull();
    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:download-test');
  });

  it('should remove the anchor and release the object URL when clicking fails', () => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:failing-download');
    const revokeObjectUrl = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {
      throw new Error('Download blocked');
    });

    expect(() => downloadTextFile('content', 'result.json', 'application/json')).toThrow(
      'Download blocked',
    );

    expect(document.querySelector('a[download="result.json"]')).toBeNull();
    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:failing-download');
  });
});
