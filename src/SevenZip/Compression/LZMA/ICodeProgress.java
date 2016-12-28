package SevenZip.Compression.LZMA;

interface ICodeProgress
{
    public void SetProgress(long inSize, long outSize);
}
